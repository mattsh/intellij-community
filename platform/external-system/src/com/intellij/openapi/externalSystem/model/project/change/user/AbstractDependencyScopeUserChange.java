/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project.change.user;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 3/4/13 12:08 PM
 */
public abstract class AbstractDependencyScopeUserChange<T extends AbstractDependencyScopeUserChange<T>>
  extends AbstractDependencyAwareUserChange<T>
{
  @Nullable
  private DependencyScope myScope;

  protected AbstractDependencyScopeUserChange() {
    // Required for IJ serialization
  }

  @SuppressWarnings("NullableProblems")
  protected AbstractDependencyScopeUserChange(@NotNull String moduleName,
                                              @NotNull String dependencyName,
                                              @NotNull DependencyScope scope)
  {
    super(moduleName, dependencyName);
    myScope = scope;
  }

  @Nullable
  public DependencyScope getScope() {
    return myScope;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setScope(@Nullable DependencyScope scope) {
    myScope = scope;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myScope != null ? myScope.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractDependencyScopeUserChange<?> change = (AbstractDependencyScopeUserChange<?>)o;

    if (myScope != null ? !myScope.equals(change.myScope) : change.myScope != null) return false;

    return true;
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public int compareTo(@NotNull UserProjectChange<?> o) {
    int cmp = super.compareTo(o);
    if (cmp != 0 || (!(o instanceof AbstractDependencyScopeUserChange<?>))) {
      return cmp;
    }

    AbstractDependencyScopeUserChange<T> that = (AbstractDependencyScopeUserChange<T>)o;
    if (myScope == null) {
      return that.myScope == null ? 0 : 1;
    }
    else if (that.myScope == null) {
      return -1;
    }
    else {
      return myScope.compareTo(that.myScope);
    }
  }
}
