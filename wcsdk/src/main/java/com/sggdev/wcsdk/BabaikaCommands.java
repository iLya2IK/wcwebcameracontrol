package com.sggdev.wcsdk;

import java.util.ArrayList;

public class BabaikaCommands {
    private ArrayList<BabaikaCommand> commands = new ArrayList<>();

    BabaikaCommands() {
        //
    }

    int getCommandsCount() {
        return commands.size();
    }

    String getCommandKey(int kn) {
        return commands.get(kn).getKey();
    }

    String getCommandValue(int kn) {
        return commands.get(kn).getCommand();
    }

    String getComment(int kn) { return commands.get(kn).getComment(); }

    String getPictureName() {return "ic_ble_device"; }

    ArrayList<BabaikaCommand> copyCommands() {
        ArrayList<BabaikaCommand> res = new ArrayList<>();
        res.addAll(commands);
        return res;
    }

    void put(String aKey, String aCommand, String aComment, String aPicture, boolean aRepeatable) {
        commands.add(new BabaikaCommand(aKey, aCommand, aComment, aPicture, aRepeatable));
    }
}