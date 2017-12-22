// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.spellchecker.util.Strings;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.OptionalChooserComponent;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

import static javax.swing.event.HyperlinkEvent.EventType.ACTIVATED;

public class SpellCheckerSettingsPane implements Disposable {
  private JPanel root;
  private JPanel linkContainer;
  private JPanel myPanelForBundledDictionaries;
  private JPanel panelForAcceptedWords;
  private JPanel myPanelForCustomDictionaries;
  private JPanel userDictionariesPanel;
  private OptionalChooserComponent<String> myBundledDictionariesChooserComponent;
  private final CustomDictionariesPanel myDictionariesPanel;
  private final List<Pair<String, Boolean>> bundledDictionaries = new ArrayList<>();
  private final WordsPanel wordsPanel;
  private final SpellCheckerManager manager;
  private final SpellCheckerSettings settings;

  public SpellCheckerSettingsPane(SpellCheckerSettings settings, final Project project) {
    this.settings = settings;
    manager = SpellCheckerManager.getInstance(project);
    HyperlinkLabel link = new HyperlinkLabel(SpellCheckerBundle.message("link.to.inspection.settings"));
    link.addHyperlinkListener(e -> {
      if (e.getEventType() == ACTIVATED) {
        Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext());
        if (allSettings != null) {
          final ErrorsConfigurable errorsConfigurable = allSettings.find(ErrorsConfigurable.class);
          if (errorsConfigurable != null) {
            allSettings.select(errorsConfigurable).doWhenDone(
              () -> errorsConfigurable.selectInspectionTool(SpellCheckingInspection.SPELL_CHECKING_INSPECTION_TOOL_NAME));
          }
        }
      }
    });
    linkContainer.setLayout(new BorderLayout());
    linkContainer.add(link);

    if (!project.isDefault()) {
      userDictionariesPanel.setVisible(true);
      userDictionariesPanel.setLayout(new VerticalFlowLayout());
      final HyperlinkLabel projectDictionaryLink = new HyperlinkLabel(SpellCheckerBundle.message("project.dictionary"));
      projectDictionaryLink.addHyperlinkListener(e -> {
        if (e.getEventType() == ACTIVATED) {
          manager.openDictionaryInEditor(manager.getProjectDictionaryPath());
        }
      });
      userDictionariesPanel.add(projectDictionaryLink);

      final HyperlinkLabel appDictionaryLink = new HyperlinkLabel(SpellCheckerBundle.message("app.dictionary"));
      appDictionaryLink.addHyperlinkListener(e -> {
        if (e.getEventType() == ACTIVATED) {
          manager.openDictionaryInEditor(manager.getAppDictionaryPath());
        }
      });
      userDictionariesPanel.add(appDictionaryLink);
    }

    // Fill in all the dictionaries folders (not implemented yet) and enabled dictionaries
    fillBundledDictionaries();

    myDictionariesPanel = new CustomDictionariesPanel(settings, project, manager);
    myPanelForCustomDictionaries.setLayout(new BorderLayout());
    myPanelForCustomDictionaries.add(myDictionariesPanel, BorderLayout.CENTER);

    myBundledDictionariesChooserComponent = new OptionalChooserComponent<String>(bundledDictionaries) {
      @Override
      public JCheckBox createCheckBox(String path, boolean checked) {
        return new JCheckBox(FileUtil.toSystemDependentName(path), checked);
      }

      @Override
      public void apply() {
        super.apply();
        final HashSet<String> bundledDisabledDictionaries = new HashSet<>();
        for (Pair<String, Boolean> pair : bundledDictionaries) {
          if (!pair.second) {
            bundledDisabledDictionaries.add(pair.first);
          }
        }
        settings.setBundledDisabledDictionariesPaths(bundledDisabledDictionaries);
      }

      @Override
      public void reset() {
        super.reset();
        fillBundledDictionaries();
      }
    };

    myPanelForBundledDictionaries.setLayout(new BorderLayout());
    myPanelForBundledDictionaries.add(myBundledDictionariesChooserComponent.getContentPane(), BorderLayout.CENTER);
    myBundledDictionariesChooserComponent.getEmptyText().setText(SpellCheckerBundle.message("no.dictionaries"));


    wordsPanel = new WordsPanel(manager);
    panelForAcceptedWords.setLayout(new BorderLayout());
    panelForAcceptedWords.add(wordsPanel, BorderLayout.CENTER);

  }

  public JComponent getPane() {
    return root;
  }

  public boolean isModified() {
    return wordsPanel.isModified() || myBundledDictionariesChooserComponent.isModified() || myDictionariesPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    if (wordsPanel.isModified()){
     manager.updateUserDictionary(wordsPanel.getWords());
    }
    if (!myBundledDictionariesChooserComponent.isModified() && !myDictionariesPanel.isModified()){
      return;
    }

    myBundledDictionariesChooserComponent.apply();
    myDictionariesPanel.apply();

    manager.updateBundledDictionaries(myDictionariesPanel.getRemovedDictionaries());
  }

  public void reset() {
    myDictionariesPanel.reset();
    myBundledDictionariesChooserComponent.reset();
  }


  private void fillBundledDictionaries() {
    bundledDictionaries.clear();
    for (String dictionary : SpellCheckerManager.getBundledDictionaries()) {
      bundledDictionaries.add(Pair.create(dictionary, !settings.getBundledDisabledDictionariesPaths().contains(dictionary)));
    }
  }


  public void dispose() {
    if (wordsPanel != null) {
      Disposer.dispose(wordsPanel);
    }
  }


  private static final class WordsPanel extends AddDeleteListPanel<String> implements Disposable {
    private final SpellCheckerManager manager;

    private WordsPanel(SpellCheckerManager manager) {
      super(null, ContainerUtil.sorted(manager.getUserDictionaryWords()));
      this.manager = manager;
      getEmptyText().setText(SpellCheckerBundle.message("no.words"));
    }


    protected String findItemToAdd() {
      String word = Messages.showInputDialog(SpellCheckerBundle.message("enter.simple.word"),
                                             SpellCheckerBundle.message("add.new.word"), null);
      if (word == null) {
        return null;
      }
      else {
        word = word.trim();
      }

      if (Strings.isMixedCase(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.mixed.cased.you.must.enter.simple.word", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      if (!manager.hasProblem(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.correct.you.no.need.to.add.this.in.list", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      return word;
    }


    public void dispose() {
      myListModel.removeAllElements();
    }

    @NotNull
    public List<String> getWords() {
      Object[] pairs = getListItems();
      if (pairs == null) {
        return new ArrayList<>();
      }
      List<String> words = new ArrayList<>();
      for (Object pair : pairs) {
        words.add(pair.toString());
      }
      return words;
    }

    public boolean isModified() {
      List<String> newWords = getWords();
      Set<String> words = manager.getUserDictionaryWords();
      if (newWords.size() != words.size()) {
        return true;
      }
      return !(words.containsAll(newWords) && newWords.containsAll(words));
    }
  }
}
