package com.techyourchance.multithreading.exercises.exercise9;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.techyourchance.multithreading.DefaultConfiguration;
import com.techyourchance.multithreading.R;
import com.techyourchance.multithreading.common.BaseFragment;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

public class Exercise9Fragment extends BaseFragment {

    public static Fragment newInstance() {
        return new Exercise9Fragment();
    }

    private static int MAX_TIMEOUT_MS = DefaultConfiguration.DEFAULT_FACTORIAL_TIMEOUT_MS;

    private EditText mEdtArgument;
    private EditText mEdtTimeout;
    private Button mBtnStartWork;
    private TextView mTxtResult;

    private ComputeFactorialUseCase mComputeFactorialUseCase;
    private Disposable disposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRxJavaErrorHandler();

        mComputeFactorialUseCase = new ComputeFactorialUseCase();
    }

    private void setRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if ((e instanceof IOException) || (e instanceof SocketException)) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
                return;
            }
            Log.w("exercise 9", "Undeliverable exception received, not sure what to do", e);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercise_9, container, false);

        mEdtArgument = view.findViewById(R.id.edt_argument);
        mEdtTimeout = view.findViewById(R.id.edt_timeout);
        mBtnStartWork = view.findViewById(R.id.btn_compute);
        mTxtResult = view.findViewById(R.id.txt_result);

        mBtnStartWork.setOnClickListener(v -> {
            if (mEdtArgument.getText().toString().isEmpty()) {
                return;
            }

            mTxtResult.setText("");
            mBtnStartWork.setEnabled(false);


            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mBtnStartWork.getWindowToken(), 0);

            int argument = Integer.valueOf(mEdtArgument.getText().toString());

            disposable = mComputeFactorialUseCase.computeFactorialAndNotify(argument, getTimeout())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(myResult -> {
                        if (myResult.isTimeOut) {
                            onFactorialComputationTimedOut();
                        } else {
                            onFactorialComputed(myResult.result);
                        }
                    }, throwable -> {
                        Log.e("exercise 9", "", throwable);
                    });
        });

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        disposable.dispose();
    }

    @Override
    protected String getScreenTitle() {
        return "Exercise 9";
    }

    private int getTimeout() {
        int timeout;
        if (mEdtTimeout.getText().toString().isEmpty()) {
            timeout = MAX_TIMEOUT_MS;
        } else {
            timeout = Integer.valueOf(mEdtTimeout.getText().toString());
            if (timeout > MAX_TIMEOUT_MS) {
                timeout = MAX_TIMEOUT_MS;
            }
        }
        return timeout;
    }

    public void onFactorialComputed(BigInteger result) {
        mTxtResult.setText(result.toString());
        mBtnStartWork.setEnabled(true);
    }

    public void onFactorialComputationTimedOut() {
        mTxtResult.setText("Computation timed out");
        mBtnStartWork.setEnabled(true);
    }

    public void onFactorialComputationAborted() {
        mTxtResult.setText("Computation aborted");
        mBtnStartWork.setEnabled(true);
    }
}
