package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class AddNoInspectionDocTagAction implements IntentionAction {
  private String myDisplayName;
  private String myID;
  protected PsiElement myContext;

  public AddNoInspectionDocTagAction(LocalInspectionTool tool, PsiElement context) {
    myDisplayName = tool.getDisplayName();
    myID = tool.getID();
    myContext = context;
  }

  public AddNoInspectionDocTagAction(HighlightDisplayKey key, PsiElement context) {
    myDisplayName = HighlightDisplayKey.getDisplayNameByKey(key);
    myID = key.getID();
    myContext = context;
  }

  public AddNoInspectionDocTagAction(String displayName, String ID, PsiElement context) {
    myDisplayName = displayName;
    myID = ID;
    myContext = context;
  }

  public String getText() {
    PsiDocCommentOwner container = getContainer();

    @NonNls String key = container instanceof PsiClass
                         ? "suppress.inspection.class"
                         : container instanceof PsiMethod
                           ? "suppress.inspection.method"
                           : "suppress.inspection.field";
    return InspectionsBundle.message(key, myDisplayName);
  }

  protected PsiDocCommentOwner getContainer() {
    if (!(myContext.getContainingFile().getLanguage() instanceof JavaLanguage)){
      return null;
    }
    PsiElement container = myContext;
    do {
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
    }
    while (container instanceof PsiTypeParameter);
    return (PsiDocCommentOwner)container;
  }

  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myContext.isValid() && myContext.getManager().isInProject(myContext) && getContainer() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer();
    if (!CodeInsightUtil.prepareFileForWrite(container.getContainingFile())) return;
    PsiDocComment docComment = container.getDocComment();
    PsiManager manager = myContext.getManager();
    if (docComment == null) {
      String commentText = "/** @" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " "+ myID + "*/";
      docComment = manager.getElementFactory().createDocCommentFromText(commentText, null);
      manager.getCodeStyleManager().reformat(docComment);
      PsiElement firstChild = container.getFirstChild();
      container.addBefore(docComment, firstChild);
      manager.getCodeStyleManager().reformatRange(container,
                                                  container.getTextRange().getStartOffset(),
                                                  firstChild.getTextRange().getStartOffset());
      return;
    }

    PsiDocTag noInspectionTag = docComment.findTagByName(InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (noInspectionTag != null) {
      String tagText = "@" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " "
                       + noInspectionTag.getValueElement().getText() + ","+ myID;
      noInspectionTag.replace(manager.getElementFactory().createDocTagFromText(tagText, null));
    } else {
      String tagText = "@" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
      docComment.add(manager.getElementFactory().createDocTagFromText(tagText, null));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
