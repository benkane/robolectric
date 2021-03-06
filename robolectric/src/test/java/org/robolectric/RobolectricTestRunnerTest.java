package org.robolectric;

import android.os.Build;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.robolectric.RobolectricTestRunner.RobolectricFrameworkMethod;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ParallelUniverse;
import org.robolectric.internal.ParallelUniverseInterface;
import org.robolectric.internal.SdkConfig;
import org.robolectric.internal.SdkEnvironment;
import org.robolectric.manifest.AndroidManifest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.robolectric.util.ReflectionHelpers.callConstructor;
import static org.robolectric.util.ReflectionHelpers.newInstance;

public class RobolectricTestRunnerTest {

  private RunNotifier notifier;
  private List<String> events;

  @Before
  public void setUp() throws Exception {
    notifier = new RunNotifier();
    events = new ArrayList<>();
    notifier.addListener(new RunListener() {
      @Override
      public void testIgnored(Description description) throws Exception {
        events.add("ignored: " + description.getDisplayName());
      }

      @Override
      public void testFailure(Failure failure) throws Exception {
        events.add("failure: " + failure.getMessage());
      }
    });
  }

  @Test public void ignoredTestCanSpecifyUnsupportedSdkWithoutExploding() throws Exception {
    RobolectricTestRunner runner = new RobolectricTestRunner(TestWithOldSdk.class);
    runner.run(notifier);
    assertThat(events).containsOnly(
        "failure: Robolectric does not support API level 11.",
        "ignored: ignoredOldSdkMethod(org.robolectric.RobolectricTestRunnerTest$TestWithOldSdk)"
    );
  }

  @Test
  public void failureInResetterDoesntBreakAllTests() throws Exception {
    RobolectricTestRunner runner = new RobolectricTestRunner(TestWithTwoMethods.class) {
      @Override
      ParallelUniverseInterface getHooksInterface(SdkEnvironment sdkEnvironment) {
        Class<? extends ParallelUniverseInterface> clazz = sdkEnvironment.bootstrappedClass(MyParallelUniverse.class);
        return callConstructor(clazz, from(RobolectricTestRunner.class, this));
      }
    };
    runner.run(notifier);
    assertThat(events).containsExactly(
        "failure: java.lang.RuntimeException: fake error in resetStaticState",
        "failure: java.lang.RuntimeException: fake error in resetStaticState"
    );
  }

  @Test
  public void equalityOfRobolectricFrameworkMethod() throws Exception {
    Method method = TestWithTwoMethods.class.getMethod("first");
    RobolectricFrameworkMethod rfm16 = new RobolectricFrameworkMethod(method, mock(AndroidManifest.class), new SdkConfig(16), mock(Config.class));
    RobolectricFrameworkMethod rfm17 = new RobolectricFrameworkMethod(method, mock(AndroidManifest.class), new SdkConfig(17), mock(Config.class));
    RobolectricFrameworkMethod rfm16b = new RobolectricFrameworkMethod(method, mock(AndroidManifest.class), new SdkConfig(16), mock(Config.class));

    assertThat(rfm16).isEqualTo(rfm16);
    assertThat(rfm16).isNotEqualTo(rfm17);
    assertThat(rfm16).isEqualTo(rfm16b);

    assertThat(rfm16.hashCode()).isEqualTo((rfm16b.hashCode()));
  }

  /////////////////////////////

  public static class MyParallelUniverse extends ParallelUniverse {
    public MyParallelUniverse(RobolectricTestRunner robolectricTestRunner) {
      super(robolectricTestRunner);
    }

    @Override
    public void resetStaticState(Config config) {
      throw new RuntimeException("fake error in resetStaticState");
    }
  }

  @Ignore
  public static class TestWithOldSdk {
    @Config(sdk = Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void oldSdkMethod() throws Exception {
      fail("I should not be run!");
    }

    @Ignore("This test shouldn't run, and shouldn't cause the test runner to fail")
    @Config(sdk = Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void ignoredOldSdkMethod() throws Exception {
      fail("I should not be run!");
    }
  }

  @Ignore
  public static class TestWithTwoMethods {
    @Test
    public void first() throws Exception {
    }

    @Test
    public void second() throws Exception {
    }
  }
}