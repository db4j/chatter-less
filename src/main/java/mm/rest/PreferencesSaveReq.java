
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PreferencesSaveReq {

    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("category")
    @Expose
    public String category;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("value")
    @Expose
    public String value;

}
