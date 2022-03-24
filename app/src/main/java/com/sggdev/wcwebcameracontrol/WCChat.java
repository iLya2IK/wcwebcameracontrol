package com.sggdev.wcwebcameracontrol;

public class WCChat {

    public static class ChatMessage {
        private final int dbId;
        private String message = "";
        private DeviceItem sender = null;
        private String target = "";
        private String createdAt = "";

        ChatMessage(int aDbId) {
            dbId = aDbId;
        }

        String getMessage() { return message; }
        String getCreatedAt() { return createdAt; }
        DeviceItem getSender() { return sender; }
        int getDbId() { return dbId; }
        String getTarget() { return target; }
        String getTimeStamp() { return createdAt; }

        void setTarget(String atarget) { target = atarget; }
        void setMessage(String amsg) { message = amsg; }
        void setSender(DeviceItem asender) { sender = asender; }
        void setTimeStamp(String astamp) { createdAt = astamp; }
    }
}
