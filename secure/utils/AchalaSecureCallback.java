package com.example.achalasecure.utils;

public interface AchalaSecureCallback {
    void onCompareSuccess(String result,String score);
    void onCompareFailed(String Failed);
}
