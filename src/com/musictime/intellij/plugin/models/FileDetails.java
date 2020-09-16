package com.musictime.intellij.plugin.models;

public class FileDetails {
    public String project_directory = "";
    public String project_name = "";
    public String full_file_name = ""; // full file path
    public String project_file_name = ""; // file path after the project root
    public String file_name = ""; // the base name
    public String syntax = "";
    public int line_count = 0;
    public long character_count = 0;
}
