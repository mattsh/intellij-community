package com.intellij.refactoring.rename;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class RenameDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameDialog");
  private SuggestedNameInfo mySuggestedNameInfo;

  private JLabel myNameLabel;
  private NameSuggestionsField myNameSuggestionsField;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private JCheckBox myCbRenameVariables;
  private JCheckBox myCbRenameInheritors;
  private final JLabel myNewNamePrefix = new JLabel("");
  private final String myHelpID;
  private final PsiElement myPsiElement;
  private final PsiElement myNameSuggestionContext;
  private final Editor myEditor;
  private static final String REFACTORING_NAME = RefactoringBundle.message("rename.title");
  private NameSuggestionsField.DataChanged myNameChangedListener;
  private Map<AutomaticRenamerFactory, JCheckBox> myAutomaticRenamers = new HashMap<AutomaticRenamerFactory, JCheckBox>();

  public RenameDialog(@NotNull Project project, @NotNull PsiElement psiElement, @Nullable PsiElement nameSuggestionContext,
                      Editor editor) {
    super(project, true);

    assert psiElement.isValid();

    myPsiElement = psiElement;
    myNameSuggestionContext = nameSuggestionContext;
    myEditor = editor;
    setTitle(REFACTORING_NAME);

    createNewNameComponent();
    init();

    myNameLabel.setText(RefactoringBundle.message("rename.0.and.its.usages.to", getFullName()));
    boolean toSearchInComments = isToSearchInCommentsForRename();
    myCbSearchInComments.setSelected(toSearchInComments);

    if (myCbSearchTextOccurences.isEnabled()) {
      boolean toSearchForTextOccurences = isToSearchForTextOccurencesForRename();
      myCbSearchTextOccurences.setSelected(toSearchForTextOccurences);
    }

    validateButtons();
    myHelpID = HelpID.getRenameHelpID(psiElement);
  }

  protected void dispose() {
    myNameSuggestionsField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  protected boolean isToSearchForTextOccurencesForRename() {
    return JavaRefactoringSettings.getInstance().isToSearchForTextOccurencesForRename(myPsiElement);
  }

  protected boolean isToSearchInCommentsForRename() {
    return JavaRefactoringSettings.getInstance().isToSearchInCommentsForRename(myPsiElement);
  }

  private String getFullName() {
    final String name = UsageViewUtil.getDescriptiveName(myPsiElement);
    return (UsageViewUtil.getType(myPsiElement) + " " + name).trim();
  }

  private void createNewNameComponent() {
    String[] suggestedNames = getSuggestedNames();
    myNameSuggestionsField = new NameSuggestionsField(suggestedNames, myProject, FileTypes.PLAIN_TEXT, myEditor);
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myNameSuggestionsField.addDataChangedListener(myNameChangedListener);

    if (myPsiElement instanceof PsiVariable) {
      myNameSuggestionsField.getComponent().registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          completeVariable(myNameSuggestionsField.getEditor());
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
  }

  private void completeVariable(Editor editor) {
    String prefix = myNameSuggestionsField.getEnteredName();
    PsiVariable var = (PsiVariable)myPsiElement;
    VariableKind kind = JavaCodeStyleManager.getInstance(myProject).getVariableKind(var);
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    CompletionUtil.completeVariableNameForRefactoring(myProject, set, prefix, var.getType(), kind);

    if (prefix.length() == 0) {
      String[] suggestedNames = getSuggestedNames();
      for (String suggestedName : suggestedNames) {
        LookupItemUtil.addLookupItem(set, suggestedName, "");
      }
    }

    LookupItem[] lookupItems = set.toArray(new LookupItem[set.size()]);
    editor.getCaretModel().moveToOffset(prefix.length());
    editor.getSelectionModel().removeSelection();
    LookupManager.getInstance(myProject).showLookup(editor, lookupItems, prefix, null, new CharFilter() {
      public int accept(char c, final String prefix) {
        if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    });
  }

  private String[] getSuggestedNames() {
    String initialName = UsageViewUtil.getShortName(myPsiElement);
    mySuggestedNameInfo = suggestNamesForElement(myPsiElement);

    String parameterName = null;
    if (myNameSuggestionContext != null) {
      final PsiElement nameSuggestionContextParent = myNameSuggestionContext.getParent();
      if (nameSuggestionContextParent != null && nameSuggestionContextParent.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList expressionList = (PsiExpressionList)nameSuggestionContextParent.getParent();
        final PsiElement parent = expressionList.getParent();
        if (parent instanceof PsiCallExpression) {
          final PsiMethod method = ((PsiCallExpression)parent).resolveMethod();
          if (method != null) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
              PsiExpression expression = expressions[i];
              if (expression == nameSuggestionContextParent) {
                if (i < parameters.length) {
                  parameterName = parameters[i].getName();
                }
                break;
              }
            }
          }
        }
      }
    }
    final String[] strings = mySuggestedNameInfo != null ? mySuggestedNameInfo.names : ArrayUtil.EMPTY_STRING_ARRAY;
    ArrayList<String> list = new ArrayList<String>(Arrays.asList(strings));
    final String properlyCased = suggestProperlyCasedName(myPsiElement);
    if (!list.contains(initialName)) {
      list.add(0, initialName);
    }
    else {
      int i = list.indexOf(initialName);
      list.remove(i);
      list.add(0, initialName);
    }
    if (properlyCased != null && !properlyCased.equals(initialName)) {
      list.add(1, properlyCased);
    }
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    ContainerUtil.removeDuplicates(list);
    return list.toArray(new String[list.size()]);
  }

  private String suggestProperlyCasedName(PsiElement psiElement) {
    if (!(psiElement instanceof PsiNamedElement)) return null;
    String name = ((PsiNamedElement)psiElement).getName();
    if (name == null) return null;
    if (psiElement instanceof PsiVariable) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      final VariableKind kind = codeStyleManager.getVariableKind((PsiVariable)psiElement);
      final String prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (name.startsWith(prefix)) {
        name = name.substring(prefix.length());
      }
      final String[] words = NameUtil.splitNameIntoWords(name);
      if (kind == VariableKind.STATIC_FINAL_FIELD) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          if (i > 0) buffer.append('_');
          buffer.append(word.toUpperCase());
        }
        return buffer.toString();
      }
      else {
        StringBuilder buffer = new StringBuilder(prefix);
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          final boolean prefixRequiresCapitalization = prefix.length() > 0 && !StringUtil.endsWithChar(prefix, '_');
          if (i > 0 || prefixRequiresCapitalization) {
            buffer.append(StringUtil.capitalize(word));
          }
          else {
            buffer.append(StringUtil.decapitalize(word));
          }
        }
        return buffer.toString();
      }

    }
    return name;
  }

  private SuggestedNameInfo suggestNamesForElement(final PsiElement element) {
    PsiVariable var = null;
    if (element instanceof PsiVariable) {
      var = (PsiVariable)element;
    }
    else if (element instanceof PsiIdentifier) {
      PsiIdentifier identifier = (PsiIdentifier)element;
      if (identifier.getParent() instanceof PsiVariable) {
        var = (PsiVariable)identifier.getParent();
      }
    }

    if (var == null) return null;

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    VariableKind variableKind = codeStyleManager.getVariableKind(var);
    return codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
  }

  public String getNewName() {
    return myNameSuggestionsField.getEnteredName().trim();
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }
  private boolean isToRenameInheritors() {
    return JavaRefactoringSettings.getInstance().isToRenameInheritors(myPsiElement);
  }

  private boolean isToRenameVariables() {
    return JavaRefactoringSettings.getInstance().isToRenameVariables(myPsiElement);
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private boolean shouldRenameVariables() {
    return myCbRenameVariables != null && myCbRenameVariables.isSelected();
  }

  private boolean shouldRenameInheritors() {
    return myCbRenameInheritors != null && myCbRenameInheritors.isSelected();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    panel.setBorder(IdeBorderFactory.createBorder());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myNameLabel = new JLabel();
    panel.add(myNameLabel, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, "".equals(myNewNamePrefix.getText()) ? 0 : 1);
    gbConstraints.gridwidth = 1;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myNewNamePrefix, gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 0;
    panel.add(myNameSuggestionsField.getComponent(), gbConstraints);

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchInComments = new NonFocusableCheckBox();
    myCbSearchInComments.setText(RefactoringBundle.getSearchInCommentsAndStringsText());
    myCbSearchInComments.setSelected(true);
    panel.add(myCbSearchInComments, gbConstraints);

    gbConstraints.insets = new Insets(4, 4, 4, 8);
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;
    myCbSearchTextOccurences = new NonFocusableCheckBox();
    myCbSearchTextOccurences.setText(RefactoringBundle.getSearchForTextOccurrencesText());
    myCbSearchTextOccurences.setSelected(true);
    panel.add(myCbSearchTextOccurences, gbConstraints);
    if (!TextOccurrencesUtil.isSearchTextOccurencesEnabled(myPsiElement)) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setSelected(false);
      myCbSearchTextOccurences.setVisible(false);
    }

    if (myPsiElement instanceof PsiClass) {
      gbConstraints.insets = new Insets(4, 8, 4, 8);
      gbConstraints.gridwidth = 1;
      gbConstraints.gridx = 0;
      gbConstraints.weightx = 1;
      gbConstraints.fill = GridBagConstraints.BOTH;
      myCbRenameVariables = new NonFocusableCheckBox();
      myCbRenameVariables.setText(RefactoringBundle.message("rename.variables"));
      myCbRenameVariables.setSelected(isToRenameVariables());
      panel.add(myCbRenameVariables, gbConstraints);

      gbConstraints.insets = new Insets(4, 4, 4, 8);
      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gbConstraints.gridx = 1;
      gbConstraints.weightx = 1;
      gbConstraints.fill = GridBagConstraints.BOTH;
      myCbRenameInheritors = new NonFocusableCheckBox();
      myCbRenameInheritors.setText(RefactoringBundle.message("rename.inheritors"));
      myCbRenameInheritors.setSelected(isToRenameInheritors());
      panel.add(myCbRenameInheritors, gbConstraints);
    }

    for(AutomaticRenamerFactory factory: Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      if (factory.isApplicable(myPsiElement)) {
        gbConstraints.insets = new Insets(4, 8, 4, 8);
        gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
        gbConstraints.gridx = 0;
        gbConstraints.weightx = 1;
        gbConstraints.fill = GridBagConstraints.BOTH;

        JCheckBox checkBox = new NonFocusableCheckBox();
        checkBox.setText(factory.getOptionName());
        checkBox.setSelected(factory.isEnabled());
        panel.add(checkBox, gbConstraints);
        myAutomaticRenamers.put(factory, checkBox);
      }
    }

    return panel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  protected void doAction() {
    LOG.assertTrue(myPsiElement.isValid());

    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();

    settings.setToSearchInCommentsForRename(myPsiElement, isSearchInComments());
    if (myCbSearchTextOccurences.isEnabled()) {
      settings.setToSearchInNonJavaFilesForRename(myPsiElement, isSearchInNonJavaFiles());
    }
    if (mySuggestedNameInfo != null) {
      mySuggestedNameInfo.nameChoosen(getNewName());
    }
    if (myCbRenameInheritors != null) {
      settings.setRenameInheritors(myCbRenameInheritors.isSelected());
    }
    if (myCbRenameVariables != null) {
      settings.setRenameVariables(myCbRenameVariables.isSelected());
    }

    final RenameProcessor processor = new RenameProcessor(getProject(), myPsiElement, getNewName(), isSearchInComments(),
                                                          isSearchInNonJavaFiles());
    processor.setShouldRenameInheritors(shouldRenameInheritors());
    processor.setShouldRenameVariables(shouldRenameVariables());

    for(Map.Entry<AutomaticRenamerFactory, JCheckBox> e: myAutomaticRenamers.entrySet()) {
      if (e.getValue().isSelected()) {
        processor.addRenamerFactory(e.getKey());
      }
    }

    invokeRefactoring(processor);
  }

  protected boolean areButtonsValid() {
    final String newName = getNewName();
    return RefactoringUtil.isValidName(myProject, myPsiElement, newName);
  }

}
