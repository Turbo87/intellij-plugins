package org.angularjs.index;

import com.intellij.lang.javascript.psi.JSImplicitElementProvider;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public class AngularModuleIndex extends AngularIndexBase {
  public static final StubIndexKey<String, JSImplicitElementProvider> KEY = StubIndexKey.createIndexKey("angularjs.module.index");

  @NotNull
  @Override
  public StubIndexKey<String, JSImplicitElementProvider> getKey() {
    return KEY;
  }
}
