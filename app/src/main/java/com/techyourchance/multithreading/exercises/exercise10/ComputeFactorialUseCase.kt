package com.techyourchance.multithreading.exercises.exercise10

import android.util.Log
import java.math.BigInteger

import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import java.lang.Long.max

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

    @Throws(TimeoutCancellationException::class)
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

        return computeFinalResultAsync().await()
    }

    @WorkerThread
    @Throws(TimeoutCancellationException::class)
    private suspend fun startComputation() {
        for (i in 0 until numberOfThreads) {
            withContext(Dispatchers.IO) {
                val rangeStart = threadsComputationRanges[i]!!.start
                val rangeEnd = threadsComputationRanges[i]!!.end
                var product = BigInteger("1")

                    for (num in rangeStart..rangeEnd) {
                        val timeMillis = getRemainingTime()
                        Log.d("startComputation", "time remaining is $timeMillis")
//                        try {
                            withTimeout(timeMillis) {
                                product = product.multiply(BigInteger(num.toString()))
                            }
//                        } catch (exception: TimeoutCancellationException) {
//                            Log.e("startComputation", "failed", exception)
//                            throw exception
//                        }
                    }
                threadsComputationResults[i] = product

                numOfFinishedThreads++
            }
        }
    }

    private fun getRemainingTime(): Long {
//        return max(computationTimeoutTime - System.currentTimeMillis(), 0)
        return computationTimeoutTime - System.currentTimeMillis()
    }

    @WorkerThread
    @Throws(TimeoutCancellationException::class)
    private fun computeFinalResultAsync(): Deferred<BigInteger> {
        return GlobalScope.async(Dispatchers.IO) {
            var result = BigInteger("1")
                for (i in 0 until numberOfThreads) {
                    val timeMillis = getRemainingTime()
                    Log.d("computeFinalResultAsync", "time remaining is $timeMillis")
//                    try {
                        withTimeout(timeMillis) {
                            result = result.multiply(threadsComputationResults[i])
                        }
//                    } catch (exception: TimeoutCancellationException) {
//                        Log.e("computeFinalResultAsync", "failed", exception)
//                        throw exception
//                    }
                }

            return@async result
        }
    }

    private fun isTimedOut(): Boolean {
        return System.currentTimeMillis() >= computationTimeoutTime
    }

    private data class ComputationRange(val start: Long, val end: Long)
}
