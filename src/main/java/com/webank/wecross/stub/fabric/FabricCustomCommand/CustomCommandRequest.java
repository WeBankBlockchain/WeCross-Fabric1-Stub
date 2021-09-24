package com.webank.wecross.stub.fabric.FabricCustomCommand;

import java.util.List;

public class CustomCommandRequest {
    private String path;
    private String Command;
    private List<Object> args;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCommand() {
        return Command;
    }

    public void setCommand(String command) {
        Command = command;
    }

    public List<Object> getArgs() {
        return args;
    }

    public void setArgs(List<Object> args) {
        this.args = args;
    }
}
