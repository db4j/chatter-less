
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PreferencesChangedData {

    @SerializedName("preferences")
    @Expose
    public String preferences;

    /**
     * No args constructor for use in serialization
     * 
     */
    public PreferencesChangedData() {
    }

    /**
     * 
     * @param preferences
     */
    public PreferencesChangedData(String preferences) {
        super();
        this.preferences = preferences;
    }

}
