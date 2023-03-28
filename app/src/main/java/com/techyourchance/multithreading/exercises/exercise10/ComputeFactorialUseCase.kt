package com.techyourchance.multithreading.exercises.exercise10

import android.os.Handler
import android.os.Looper

import com.techyourchance.multithreading.common.BaseObservable

import java.math.BigInteger

import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ComputeFactorialUseCase {

    private var numberOfThreads: Int = 0
    private var threadsComputationRanges: Array<ComputationRange?> = arrayOf()
    @Volatile
    private var threadsComputationResults: Array<BigInteger?> = arrayOf()
    private var numOfFinishedThreads = 0

    private var computationTimeoutTime: Long = 0

    private var abortComputation: Boolean = false

    interface Listener {
        fun onFactorialComputed(result: BigInteger)
        fun onFactorialComputationTimedOut()
        fun onFactorialComputationAborted()
    }

    suspend fun computeFactorialAndNotify(factorialArgument: Int, timeout: Int): BigInteger {
        withContext(Dispatchers.IO) {
            numberOfThreads = if (factorialArgument < 20)
                1
            else
                Runtime.getRuntime().availableProcessors()

            threadsComputationResults = arrayOfNulls(numberOfThreads)

            threadsComputationRanges = arrayOfNulls(numberOfThreads)

            val computationRangeSize = factorialArgument / numberOfThreads

            var nextComputationRangeEnd = factorialArgument.toLong()
            for (i in numberOfThreads - 1 downTo 0) {
                threadsComputationRanges[i] = ComputationRange(
                    nextComputationRangeEnd - computationRangeSize + 1,
                    nextComputationRangeEnd
                )
                nextComputationRangeEnd = threadsComputationRanges[i]!!.start - 1
            }

            // add potentially "remaining" values to first thread's range
            threadsComputationRanges[0] = ComputationRange(1, threadsComputationRanges[0]!!.end)

            computationTimeoutTime = System.currentTimeMillis() + timeout

        }

        startComputation()

        val result = GlobalScope.async { computeFinalResult() }

        return result.await()
    }

    @WorkerThread
    private suspend fun startComputation() {
        for (i in 0 until numberOfThreads) {
            withContext(Dispatchers.IO) {
                val rangeStart = threadsComputationRanges[i]!!.start
                val rangeEnd = threadsComputationRanges[i]!!.end
                var product = BigInteger("1")
                for (num in rangeStart..rangeEnd) {
                    if (isTimedOut()) {
                        break
                    }
                    product = product.multiply(BigInteger(num.toString()))
                }
                threadsComputationResults[i] = product

                numOfFinishedThreads++
            }
        }
    }

    @WorkerThread
    private fun computeFinalResult(): BigInteger {
        var result = BigInteger("1")
        for (i in 0 until numberOfThreads) {
            if (isTimedOut()) {
                break
            }
            result = result.multiply(threadsComputationResults[i])
        }
        return result
    }

    private fun isTimedOut(): Boolean {
        return System.currentTimeMillis() >= computationTimeoutTime
    }

    private data class ComputationRange(val start: Long, val end: Long)
}
