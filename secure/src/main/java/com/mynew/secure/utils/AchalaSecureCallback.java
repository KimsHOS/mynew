package com.mynew.secure.utils;

public interface AchalaSecureCallback {
    void onCompareSuccess(String result,String score);
    void onCompareFailed(String Failed);
}
