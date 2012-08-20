/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:18 PM
 */
public class ArrangementSettingsCompositeNode implements ArrangementSettingsNode {

  @NotNull private final Set<ArrangementSettingsNode> myOperands = new HashSet<ArrangementSettingsNode>();
  @NotNull private final Operator myOperator;

  public ArrangementSettingsCompositeNode(@NotNull Operator operator) {
    myOperator = operator;
  }

  @NotNull
  public Set<ArrangementSettingsNode> getOperands() {
    return myOperands;
  }

  public ArrangementSettingsCompositeNode addOperand(@NotNull ArrangementSettingsNode node) {
    myOperands.add(node);
    return this;
  }

  @NotNull
  public Operator getOperator() {
    return myOperator;
  }

  @Override
  public void invite(@NotNull ArrangementSettingsNodeVisitor visitor) {
    visitor.visit(this);
  }
  
  @NotNull
  @Override
  public ArrangementSettingsCompositeNode clone() {
    ArrangementSettingsCompositeNode result = new ArrangementSettingsCompositeNode(myOperator);
    for (ArrangementSettingsNode operand : myOperands) {
      result.addOperand(operand.clone());
    }
    return result;
  }

  @Override
  public int hashCode() {
    int result = myOperands.hashCode();
    result = 31 * result + myOperator.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrangementSettingsCompositeNode node = (ArrangementSettingsCompositeNode)o;

    if (!myOperands.equals(node.myOperands)) {
      return false;
    }
    if (myOperator != node.myOperator) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myOperands, myOperator == Operator.AND ? " and " : " or "));
  }

  public enum Operator {
    AND, OR
  }
}