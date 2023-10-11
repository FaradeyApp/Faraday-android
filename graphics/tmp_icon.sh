#!/usr/bin/zsh
# Using zsh allows floating point multiplication in $((...))

mydir="$(dirname "$(realpath "$0")")"

base_folder="$mydir/../vector-app/src/main/res/mipmap"

file="$1"

export_png_files() {
    newfile="$1"
    mdpi_w="$2"
    mdpi_h="$3"
    if [ -z "$mdpi_h" ]; then
        mdpi_h="$mdpi_w"
    fi
    mkdir -p $base_folder-mdpi
    mkdir -p $base_folder-hdpi
    mkdir -p $base_folder-xhdpi
    mkdir -p $base_folder-xxhdpi
    mkdir -p $base_folder-xxxhdpi
    convert "$file" -resize "${mdpi_w}x${mdpi_h}" "$base_folder-mdpi/$newfile"
    convert "$file" -resize "${$((mdpi_w*1.5))%.*}x${$((mdpi_h*1.5))%.*}" "$base_folder-hdpi/$newfile"
    convert "$file" -resize "${$((mdpi_w*2))%.*}x${$((mdpi_h*2))%.*}" "$base_folder-xhdpi/$newfile"
    convert "$file" -resize "${$((mdpi_w*3))%.*}x${$((mdpi_h*3))%.*}" "$base_folder-xxhdpi/$newfile"
    convert "$file" -resize "${$((mdpi_w*4))%.*}x${$((mdpi_h*4))%.*}" "$base_folder-xxxhdpi/$newfile"
}

export_png_files "ic_launcher_sc.png" 48

file2="fg_$file"
convert "$file" -gravity center -background none -extent 150%x150% "$file2"
file="$file2"
export_png_files "ic_launcher_foreground_sc.png" 72
rm "$file2"
