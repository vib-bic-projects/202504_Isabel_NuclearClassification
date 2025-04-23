// @ File(label="File directory", style="directory") dir
// @ String (label="What working station are you using?", choices={"PC7_Paris", "PC_Nicolas", "NAS_Tokyo", "PC2_Mexico"}, style="listBox") pc
// @String (visibility=MESSAGE, value="Parameters for blob segmentation", required=false) msg1
// @Integer (label="Channel containing the nuclear marker for cell detection", min=1, max=4, value=4) blob_channel

// @ String (label="File suffix", choices={".nd2", ".tif"}, style="listBox") suffix

/* 23/04/2025
Nicolas Peredo
VIB BioImaging Core Leuven - Center for Brain and Disease Research
Nikon Center of Excellence
Campus Gasthuisberg - ON5 - room 04.367
Herestraat 49 - box 62
3000 Leuven
Belgium
phone +32 (0)16/37.70.03

When you publish data analyzed with this script please add the references of the used plug-ins:

This script takes a multiple channel image containing a channel with a nuclear marker to enhance. The script generates the image with a new channel with enhanced nuclei for easier segmentation in Qupath.
*/

//Get directories and lists of files
setOption("ExpandableArrays", true);

//Blood vessel directory
fileList = getFilesList(dir, suffix);
Array.sort(fileList);

//Create the different folders with results
File.makeDirectory(dir + "/Processed");

//GPU parameters
computerarray = newArray("PC7_Paris", "PC_Nicolas", "NAS_Tokyo", "PC2_Mexico");
gpuparameters = newArray("[NVIDIA GeForce RTX 3090]", "[NVIDIA GeForce RTX 3070]", "[Quadro RTX 8000]", "[Quadro K2200]");
gpu = "";
for (i = 0; i < computerarray.length; i++) {
	if (computerarray[i] == pc) {
		gpu = gpuparameters[i];
}

for (files = 0; files < fileList.length; files++) {
//for (files = 80; files < 81; files++) {
	
	//File and ROI names
	file = fileList[files];
	name = getBasename(file, suffix);
	
	//Open image
	run("Bio-Formats Importer", "open=[" + dir + File.separator + file + "] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT series_1");
	rename("Raw_Image");
	
	//Get parameters from image
	getDimensions(width, height, channels, slices, frames);
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//setBatchMode(true);
	
	//Detecting the MecP2 signal
	//Get MecP2 channel
	selectWindow("Raw_Image");
	run("Duplicate...", "title=Blob_channel duplicate channels=" + blob_channel + "-" + blob_channel);
	
	//Enhance image
	enhance_speckels("Blob_channel",gpu);
	rename("enhanced_image");
	
	//Generate the processed image with the added channel
	mergestring = "";
	for (channel = 0; channel < channels; channel++) {
		selectWindow("Raw_Image");
		run("Duplicate...", "duplicate channels=" + (channel+1) + "-" + (channel+1));
		rename("channel_" + channel);
		
		channel_string = "c" + (channel+1) + "=[channel_" + channel + "] ";
		mergestring = mergestring + channel_string;
	}
	mergestring = mergestring + "c" + (channel+1) + "=[enhanced_image] create keep";
	run("Merge Channels...", mergestring);
	rename(name);
	
	//Reassign pixel size since the generated filtered image is not calibrated anymore
	Stack.setXUnit("micron");
	run("Properties...", "channels=" + channels+1 + " slices=1 frames=1 pixel_width=" + pixelWidth + " pixel_height=" + pixelWidth + " voxel_depth=1.0000000");

	//Process to become 32-bit for intensity measurements
	run("Kheops - Convert Image to Pyramidal OME TIFF", "output_dir=" + dir + "/Processed/" + " compression=Uncompressed subset_channels= subset_slices= subset_frames= compress_temp_files=false");

	//Close non important windows
	close("*");
	run("Collect Garbage");
}
	
//Extract a string from another string at the given input smaller string (eg ".")
function getBasename(filename, SubString){
  dotIndex = indexOf(filename, SubString);
  basename = substring(filename, 0, dotIndex);
  return basename;
}

//Return a file list contain in the directory dir filtered by extension.
function getFilesList(dir, fileExtension) {  
  tmplist=getFileList(dir);
  list = newArray(0);
  imageNr=0;
  for (i=0; i<tmplist.length; i++)
  {
    if (endsWith(tmplist[i], fileExtension)==true)
    {
      list[imageNr]=tmplist[i];
      imageNr=imageNr+1;
      //print(tmplist[i]);
    }
  }
  Array.sort(list);
  return list;
}

function enhance_speckels(blob_image,gpu) { 
	selectWindow(blob_image);
	rename("blob_image");
	
	run("Subtract Background...", "rolling=50");
}