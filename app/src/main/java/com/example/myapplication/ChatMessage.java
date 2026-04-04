package com.example.myapplication;
import android.graphics.Bitmap;

public class ChatMessage {
    public static final int TYPE_MINE = 0;
    public static final int TYPE_OTHER = 1;
    private String content;
    private Bitmap image;
    private int type;
    private boolean isImg;

    public ChatMessage(String c, int t, boolean i, Bitmap b) { this.content = c; this.type = t; this.isImg = i; this.image = b; }
    public String getContent() { return content; }
    public int getType() { return type; }
    public boolean isImg() { return isImg; }
    public Bitmap getImage() { return image; }
}