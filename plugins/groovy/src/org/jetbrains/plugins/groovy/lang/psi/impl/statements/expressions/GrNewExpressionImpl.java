/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {

  private static final Function<GrNewExpressionImpl,PsiType> MY_TYPE_CALCULATOR = new NullableFunction<GrNewExpressionImpl, PsiType>() {
    @Override
    public PsiType fun(GrNewExpressionImpl newExpression) {
      final GrAnonymousClassDefinition anonymous = newExpression.getAnonymousClassDefinition();
      if (anonymous != null) {
        return anonymous.getBaseClassType();
      }
      PsiType type = null;
      GrCodeReferenceElement refElement = newExpression.getReferenceElement();
      if (refElement != null) {
        type = new GrClassReferenceType(refElement);
      }
      else {
        GrBuiltInTypeElement builtin = newExpression.findChildByClass(GrBuiltInTypeElement.class);
        if (builtin != null) type = builtin.getType();
      }

      if (type != null) {
        for (int i = 0; i < newExpression.getArrayCount(); i++) {
          type = type.createArrayType();
        }
        return type;
      }

      return null;
    }
  };

  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "NEW expression";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, MY_TYPE_CALCULATOR);
  }

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    final GrArgumentList list = getArgumentList();
    if (list == null) { //so it is not anonymous class declaration
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      PsiElement last = getLastChild();
      assert last != null;
      while (last.getPrevSibling() instanceof PsiWhiteSpace || last.getPrevSibling() instanceof PsiErrorElement) {
        last = last.getPrevSibling();
        assert last != null;
      }
      ASTNode astNode = last.getNode();
      assert astNode != null;
      getNode().addChild(newList.getNode(), astNode);
    }
    return super.addNamedArgument(namedArgument);
  }

  @Override
  public GrArgumentList getArgumentList() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getArgumentListGroovy();
    return super.getArgumentList();
  }

  @Nullable
  public GrExpression getQualifier() {
    final PsiElement[] children = getChildren();
    for (PsiElement child : children) {
      if (child instanceof GrExpression) return (GrExpression)child;
      if (PsiKeyword.NEW.equals(child.getText())) return null;
    }
    return null;
  }

  public GrCodeReferenceElement getReferenceElement() {
    final GrAnonymousClassDefinition anonymous = getAnonymousClassDefinition();
    if (anonymous != null) return anonymous.getBaseClassReferenceGroovy();
    return findChildByClass(GrCodeReferenceElement.class);
  }

  public GroovyResolveResult[] multiResolveClass() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement != null) {
      return referenceElement.multiResolve(false);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  public int getArrayCount() {
    final GrArrayDeclaration arrayDeclaration = getArrayDeclaration();
    if (arrayDeclaration == null) return 0;
    return arrayDeclaration.getArrayCount();
  }

  public GrAnonymousClassDefinition getAnonymousClassDefinition() {
    return findChildByClass(GrAnonymousClassDefinition.class);
  }

  @Nullable
  @Override
  public GrArrayDeclaration getArrayDeclaration() {
    return findChildByClass(GrArrayDeclaration.class);
  }

  @Nullable
  @Override
  public GrTypeArgumentList getConstructorTypeArguments() {
    return findChildByClass(GrTypeArgumentList.class);
  }

  @Nullable
  public PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }

  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return GroovyResolveResult.EMPTY_ARRAY;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    for (GroovyResolveResult classResult : referenceElement.multiResolve(false)) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        ContainerUtil.addAll(result, ResolveUtil.getAllClassConstructors((PsiClass)element, this, classResult.getSubstitutor(), null));
      }
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  @Override
  public GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    //ResolveCache.getInstance(getProject()).resolveWithCaching()
    GrCodeReferenceElement ref = getReferenceElement();
    if (ref == null) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult[] classResults = ref.multiResolve(false);
    if (classResults.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    if (incompleteCode) {
      return PsiUtil.getConstructorCandidates(ref, classResults, null);
    }

    final GrArgumentList argumentList = getArgumentList();
    if (argumentList == null) return GroovyResolveResult.EMPTY_ARRAY;

    if (argumentList.getNamedArguments().length > 0 && argumentList.getExpressionArguments().length == 0) {
      PsiType mapType = new GrMapType(argumentList, getNamedArguments());
      GroovyResolveResult[] constructorResults = PsiUtil.getConstructorCandidates(ref, classResults, new PsiType[]{mapType}); //one Map parameter, actually
      for (GroovyResolveResult result : constructorResults) {
        final PsiElement resolved = result.getElement();
        if (resolved instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)resolved;
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1 && InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
            return constructorResults;
          }
        }
      }
      final GroovyResolveResult[] emptyConstructors = PsiUtil.getConstructorCandidates(ref, classResults, PsiType.EMPTY_ARRAY);
      if (emptyConstructors.length > 0) {
        return emptyConstructors;
      }
    }

    return PsiUtil.getConstructorCandidates(ref, classResults, PsiUtil.getArgumentTypes(ref, true));
  }

  private class MyRef implements PsiPolyVariantReference {
    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return GrNewExpressionImpl.this.multiResolve(incompleteCode);
    }

    @Override
    public PsiElement getElement() {
      return GrNewExpressionImpl.this;
    }

    @Override
    public TextRange getRangeInElement() {
      return getReferenceElement().getRangeInElement();
    }

    @Override
    public PsiElement resolve() {
      return resolveMethod();
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return "new expression reference";
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return GrNewExpressionImpl.this;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return GrNewExpressionImpl.this;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return getManager().areElementsEquivalent(resolveMethod(), element);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return multiResolve(true);
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
