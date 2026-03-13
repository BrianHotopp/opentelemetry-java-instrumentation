/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.zio.v2_0

import io.opentelemetry.instrumentation.testing.junit._
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName
import io.opentelemetry.javaagent.instrumentation.zio.v2_0.ZioTestFixtures._
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.{Test, TestInstance}

import java.util.function.Consumer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZioRuntimeInstrumentationTest {

  @RegisterExtension
  val testing: InstrumentationExtension = AgentInstrumentationExtension.create()

  @Test
  def traceIsPropagatedToChildFiber(): Unit = {
    runNestedFibers()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def traceIsPreservedWhenFiberIsInterrupted(): Unit = {
    runInterruptedFiber()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_1").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def synchronizedFibersDoNotInterfereWithEachOthersTraces(): Unit = {
    runSynchronizedFibers()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def concurrentFibersDoNotInterfereWithEachOthersTraces(): Unit = {
    runConcurrentFibers()

    testing.waitAndAssertSortedTraces(
      orderByRootSpanName("fiber_1_span_1", "fiber_2_span_1", "fiber_3_span_1"),
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_3_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_3_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def sequentialFibersDoNotInterfereWithEachOthersTraces(): Unit = {
    runSequentialFibers()

    testing.waitAndAssertTraces(
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_1_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_1_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_2_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_2_span_2").hasParent(trace.getSpan(0)))
        )
      },
      assertTrace { trace =>
        trace.hasSpansSatisfyingExactly(
          assertSpan(_.hasName("fiber_3_span_1").hasNoParent),
          assertSpan(_.hasName("fiber_3_span_2").hasParent(trace.getSpan(0)))
        )
      }
    )
  }

  @Test
  def unsafeRunShouldNotDestroyCallerThreadContext(): Unit = {
    val (traceIdBefore, traceIdAfter) = runUnsafeRunPreservesCallerContext()

    assertEquals(
      traceIdBefore,
      traceIdAfter,
      "Runtime.default.unsafe.run() should not destroy the calling thread's OTel context. " +
        "onSuspend() calls Context.root().makeCurrent() when the fiber completes, " +
        "which wipes pre-existing context on the calling thread."
    )
  }

  private def assertTrace(f: TraceAssert => Any): Consumer[TraceAssert] =
    (t: TraceAssert) => f(t)

  private def assertSpan(f: SpanDataAssert => Any): Consumer[SpanDataAssert] =
    (t: SpanDataAssert) => f(t)

}
