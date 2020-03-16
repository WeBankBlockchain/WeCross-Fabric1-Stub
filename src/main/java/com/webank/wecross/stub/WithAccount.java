package com.webank.wecross.stub;

import com.webank.wecross.account.Account;

public class WithAccount<T> {
    private String path;
    private Account account;
    private T data;

    public WithAccount(String path, T data, Account account) {
        this.path = path;
        this.data = data;
        this.account = account;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
