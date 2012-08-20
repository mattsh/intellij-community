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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.arrangement.model.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:53 PM
 */
public class ArrangementNodeComponentFactory {

  @NotNull private final ArrangementNodeDisplayManager myDisplayManager;

  public ArrangementNodeComponentFactory(@NotNull ArrangementNodeDisplayManager manager) {
    myDisplayManager = manager;
  }

  @NotNull
  public ArrangementNodeComponent getComponent(@NotNull ArrangementSettingsNode node) {
    final Ref<ArrangementNodeComponent> ref = new Ref<ArrangementNodeComponent>();
    node.invite(new ArrangementSettingsNodeVisitor() {
      @Override
      public void visit(@NotNull ArrangementSettingsAtomNode node) {
        ref.set(new ArrangementAtomNodeComponent(myDisplayManager, node));
      }

      @Override
      public void visit(@NotNull ArrangementSettingsCompositeNode node) {
        switch (node.getOperator()) {
          case AND: ref.set(new ArrangementAndNodeComponent(node, ArrangementNodeComponentFactory.this, myDisplayManager)); break;
          case OR: // TODO den implement
        }
      }
    });
    return ref.get();
  }
}