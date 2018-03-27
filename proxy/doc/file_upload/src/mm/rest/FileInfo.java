
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FileInfo {

    @SerializedName("id")
    @Expose
    public String id;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("create_at")
    @Expose
    public long createAt;
    @SerializedName("update_at")
    @Expose
    public long updateAt;
    @SerializedName("delete_at")
    @Expose
    public long deleteAt;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("extension")
    @Expose
    public String extension;
    @SerializedName("size")
    @Expose
    public long size;
    @SerializedName("mime_type")
    @Expose
    public String mimeType;
    @SerializedName("width")
    @Expose
    public long width;
    @SerializedName("height")
    @Expose
    public long height;
    @SerializedName("has_preview_image")
    @Expose
    public boolean hasPreviewImage;

}
