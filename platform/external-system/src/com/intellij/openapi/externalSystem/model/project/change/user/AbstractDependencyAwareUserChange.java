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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/19/13 8:54 AM
 */
public abstract class AbstractDependencyAwareUserChange<T extends AbstractDependencyAwareUserChange<T>>
  extends AbstractModuleAwareUserChange<T>
{

  @Nullable
  private String myDependencyName;

  protected AbstractDependencyAwareUserChange() {
    // Required for IJ serialization
  }

  @SuppressWarnings("NullableProblems")
  protected AbstractDependencyAwareUserChange(@NotNull String moduleName, @NotNull String dependencyName) {
    super(moduleName);
    myDependencyName = dependencyName;
  }

  @Nullable
  public String getDependencyName() {
    return myDependencyName;
  }

  public void setDependencyName(@Nullable String dependencyName) {
    myDependencyName = dependencyName;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myDependencyName != null ? myDependencyName.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractDependencyAwareUserChange change = (AbstractDependencyAwareUserChange)o;

    if (myDependencyName != null ? !myDependencyName.equals(change.myDependencyName) : change.myDependencyName != null) return false;

    return true;
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public int compareTo(@NotNull UserProjectChange<?> o) {
    int cmp = super.compareTo(o);
    if (cmp != 0 || (!(o instanceof AbstractDependencyAwareUserChange))) {
      return cmp;
    }

    AbstractDependencyAwareUserChange<T> that = (AbstractDependencyAwareUserChange<T>)o;
    if (myDependencyName == null) {
      return that.myDependencyName == null ? 0 : 1;
    }
    else if (that.myDependencyName == null) {
      return -1;
    }
    else {
      return myDependencyName.compareTo(that.myDependencyName);
    }
  }
}
