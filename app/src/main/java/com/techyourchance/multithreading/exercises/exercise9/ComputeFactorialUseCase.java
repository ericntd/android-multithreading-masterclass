package com.techyourchance.multithreading.exercises.exercise9;

import android.util.Log;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.WorkerThread;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import kotlin.Pair;

public class ComputeFactorialUseCase {
    private long mComputationTimeoutTime;

    public Single<MyResult> computeFactorialAndNotify(final int factorialArgument, final int timeout) {
        return Single.fromCallable(() -> {
                    int mNumberOfThreads = factorialArgument < 20
                            ? 1 : Runtime.getRuntime().availableProcessors();

                    ComputationRange[] mThreadsComputationRanges = new ComputationRange[mNumberOfThreads];

                    int computationRangeSize = factorialArgument / mNumberOfThreads;

                    long nextComputationRangeEnd = factorialArgument;
                    for (int i = mNumberOfThreads - 1; i >= 0; i--) {
                        mThreadsComputationRanges[i] = new ComputationRange(
                                nextComputationRangeEnd - computationRangeSize + 1,
                                nextComputationRangeEnd
                        );
                        nextComputationRangeEnd = mThreadsComputationRanges[i].start - 1;
                    }

                    // add potentially "remaining" values to first thread's range
                    mThreadsComputationRanges[0].start = 1;
                    return mThreadsComputationRanges;
                })
                .doOnSubscribe(disposable -> mComputationTimeoutTime = System.currentTimeMillis() + timeout)
                .flatMap((Function<ComputationRange[], SingleSource<MyResult>>) ranges -> Observable.fromArray(ranges)
                        .flatMapSingle((Function<ComputationRange, SingleSource<MyResult>>) computationRange ->
                                Single.create((SingleOnSubscribe<MyResult>) emitter -> {
                                    long rangeStart = computationRange.start;
                                    long rangeEnd = computationRange.end;
                                    BigInteger product = new BigInteger("1");
                                    Log.d("ComputeFactorialUseCase", "producing on thread " + Thread.currentThread().getName());
                                    for (long num = rangeStart; num <= rangeEnd; num++) {
                                        if (isTimedOut()) {
                                            emitter.onSuccess(new MyResult(true, BigInteger.ZERO));
                                        }
                                        product = product.multiply(new BigInteger(String.valueOf(num)));
                                    }

                                    emitter.onSuccess(new MyResult(false, product));
                                }).subscribeOn(Schedulers.io())

                        )
                        .toList()
                        .flatMap((Function<List<MyResult>, SingleSource<MyResult>>) results -> {
                            if (results.get(0).isTimeOut) {
                                return Single.just(new MyResult(true, BigInteger.ZERO));
                            } else {
                                return Single.create((SingleOnSubscribe<MyResult>) emitter -> {
                                    BigInteger result = new BigInteger("1");
                                    Log.d("ComputeFactorialUseCase", "consuming on thread " + Thread.currentThread().getName());

                                    for (int i = 0; i < results.size(); i++) {
                                        if (isTimedOut()) {
                                            emitter.onSuccess(new MyResult(true, BigInteger.ZERO));
                                        }
                                        result = result.multiply(results.get(i).result);
                                    }
                                    emitter.onSuccess(new MyResult(false, result));
                                });
                            }
                        })
                );
    }

    private boolean isTimedOut() {
        return System.currentTimeMillis() >= mComputationTimeoutTime;
    }

    private static class ComputationRange {
        private long start;
        private long end;

        public ComputationRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    static class MyResult {
        boolean isTimeOut;
        BigInteger result;

        public MyResult(boolean isTimeOut, BigInteger result) {
            this.isTimeOut = isTimeOut;
            this.result = result;
        }
    }

    static class MyResult2 {
        List<MyResult> results;

        public MyResult2(List<MyResult> results) {
            this.results = results;
        }
    }
}
