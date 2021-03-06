package com.intellij.lang.javascript.flex;

import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.types.*;
import com.intellij.lang.javascript.validation.ValidateTypesUtil;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class ActionScriptExpectedTypeEvaluator extends ExpectedTypeEvaluator {
  public ActionScriptExpectedTypeEvaluator(JSExpression parent) {
    super(parent);
  }

  @Override
  protected void findRestParameterExpectedType(JSParameterItem parameterItem) {
    if (!(parameterItem instanceof JSParameter)) {
      super.findRestParameterExpectedType(parameterItem);
      return;
    }

    final JSParameter param = (JSParameter)parameterItem;
    final JSFunction fun = param.getDeclaringFunction();
    if (fun == null) {
      super.findRestParameterExpectedType(parameterItem);
      return;
    }
    PsiElement element = JSResolveUtil.findParent(fun);

    JSType classType = element instanceof JSClass ?
                       JSNamedType.createType(((JSClass)element).getQualifiedName(), JSTypeSource.EMPTY, JSContext.INSTANCE) :
                       null;
    if (classType != null && JSTypeUtils.isActionScriptVectorType(classType)) {
      String name = fun.getName();
      String qualifiedExpressionType = null;

      JSExpression methodExpression = ((JSCallExpression)JSTypeUtils.getScopeInOriginalTree(myGrandParent).getParent()).getMethodExpression();
      if (methodExpression instanceof JSReferenceExpression) {
        JSExpression qualifier = ((JSReferenceExpression)methodExpression).getQualifier();
        if (qualifier != null) {
          qualifiedExpressionType = JSResolveUtil.getQualifiedExpressionType(qualifier, qualifier.getContainingFile());
        }
      }

      if (qualifiedExpressionType != null) {
        if ("push".equals(name) || "unshift".equals(name) || "splice".equals(name)) {
          JSResolveUtil.GenericSignature signature = JSResolveUtil.extractGenericSignature(qualifiedExpressionType);
          if (signature != null) {
            myType = signature.genericType;
            myScope = methodExpression;
          }
        } else if ("concat".equals(name)) {
          myType = qualifiedExpressionType;
          myScope = methodExpression;
        }
      }
    } else {
      myType = JSCommonTypeNames.OBJECT_CLASS_NAME;
      myScope = myParent;
    }
  }

  protected void evaluateIndexedAccessType(JSIndexedPropertyAccessExpression node) {
    if (isASDictionaryAccess(node)) {
      myType = JSCommonTypeNames.OBJECT_CLASS_NAME;
    }
    else {
      final JSTypeSource typeSource = JSTypeSourceFactory.createTypeSource(myScope, true);
      myResult = new JSCompositeTypeImpl(typeSource,
                                         JSNamedType.createType(JSCommonTypeNames.INT_TYPE_NAME, typeSource, JSContext.INSTANCE),
                                         JSNamedType.createType(JSCommonTypeNames.UINT_TYPE_NAME, typeSource, JSContext.INSTANCE));
    }
  }

  private static boolean isASDictionaryAccess(final JSIndexedPropertyAccessExpression expression) {
    if (expression.getContainingFile().getLanguage() != JavaScriptSupportLoader.ECMA_SCRIPT_L4) return false;

    final JSExpression qualifier = expression.getQualifier();
    final PsiElement resolve = qualifier instanceof JSReferenceExpression ? ((JSReferenceExpression)qualifier).resolve() : null;
    final String type = resolve instanceof JSVariable ? ((JSVariable)resolve).getTypeString() : null;

    return type != null && JSResolveUtil.isAssignableType(ValidateTypesUtil.FLASH_UTILS_DICTIONARY, type, expression);
  }
}
