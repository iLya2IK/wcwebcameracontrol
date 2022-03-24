package com.sggdev.wcwebcameracontrol;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class BabaikaConfigNotif  extends BabaikaNotiCommand {

    class BabaikaConfigItem {

        public static final int CFG_OPT_PASSWORD = 1;
        public static final int CFG_OPT_READONLY = 2;

        private final String mDefValue;
        private final String mKey;
        private final String mComment;
        private final String mPicture;
        private int mOptions = 0;

        BabaikaConfigItem(String key, String picture, String comment, String defValue) {
            mKey = key;
            mDefValue = defValue;
            mComment = comment;
            mPicture = picture;
        }

        BabaikaConfigItem(BabaikaConfigItem ref) {
            mKey = ref.Key();
            mDefValue = ref.defValue();
            mComment = ref.Comment();
            mPicture = ref.getPicture();
            mOptions = ref.getOptions();
        }

        String Key() {
            return mKey;
        }

        String Comment() {
            return mComment;
        }

        String defValue() {
            return mDefValue;
        }

        String getPicture() {
            return mPicture;
        }

        void setOption(int opt) {
            mOptions |= opt;
        }

        int getOptions() {
            return mOptions;
        }
    }

    class BabaikaModConfigItem extends BabaikaConfigItem {

        private String mValue;

        BabaikaModConfigItem(String key, String picture, String comment, String defValue) {
            super(key, picture, comment, defValue);
            mValue = defValue;
        }

        BabaikaModConfigItem(BabaikaConfigItem ref) {
            super (ref);
            mValue = defValue();
        }

        String getValue() {
            return mValue;
        }

        void setValue(String aValue) {
            mValue = aValue;
        }
    }


    private final Map<String, BabaikaConfigItem> possib_fields = new HashMap<>();
    private OnFieldValueChangingListener mFieldValueChanging = null;
    private OnFieldValueChangedListener mFieldValueChanged = null;

    BabaikaConfigNotif() {
        this("");
    }

    BabaikaConfigNotif(String chuuid) {
        super("", "", "","", false);
        setNotification(new BabaikaDeviceConfigNotif(chuuid));
        setOnValueChangedListener(new OnValueChangedListener() {
            @Override
            public void onChange() {
                String v = getNoti().formatted_value;
                try {
                    JSONObject fields = new JSONObject(v);
                    for (String pfield: possib_fields.keySet()) {
                        if (fields.has(pfield)) {
                            String val = fields.optString(pfield, "");
                            BabaikaConfigItem oldval = possib_fields.get(pfield);
                            if (oldval != null)
                                doFieldValueChanging(pfield, val);
                        }
                    }
                } catch (JSONException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void doFieldValueChanged(String field, String new_value) {
        if (mFieldValueChanged != null) {
            mFieldValueChanged.onChange(field, new_value);
        }
    }


    private void doFieldValueChanging(String field, String new_value) {
        if (mFieldValueChanging != null) {
            if (mFieldValueChanging.onChange(field, new_value)) {
                doFieldValueChanged(field, new_value);
            }
        } else {
            doFieldValueChanged(field, new_value);
        }
    }

    void setFieldValueChanging(OnFieldValueChangingListener list) {
        mFieldValueChanging = list;
    }

    void setFieldValueChanged(OnFieldValueChangedListener list) {
        mFieldValueChanged = list;
    }

    BabaikaConfigItem addField(String aField, String aPicture, String aComment, String defValue) {
        BabaikaConfigItem it = new BabaikaConfigItem(aField, aPicture, aComment, defValue);
        possib_fields.put(aField, it);
        return it;
    }

    BabaikaModConfigItem genNewInstance(String aField) {
        BabaikaConfigItem val = possib_fields.get(aField);
        if (val != null)
            return new BabaikaModConfigItem(val);
        return null;
    }
}
