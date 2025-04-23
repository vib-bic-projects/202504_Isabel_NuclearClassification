# 202504_Isabel_NuclearClassification
Automatic cell counting for cells positive for different markers

## (Pre-processing) Nuclear Channel Enhancement (FIJI) 
This first step takes .nd2 raw images and applies a small enhancement of the nuclear channel you select and adds the processed channel as a fifth channel so that you have access to the raw signal when you analyze.
- Drag and drop "202504_Isabel_NuclearClassification/scripts/01_process_script.ijm" to FIJI.
- Click "Run"
- Set the proper parameters in the user interface:
![image](https://github.com/user-attachments/assets/dd618ec9-66f1-4992-a408-bf9cc1c29c83)

**File directory:** File path location

**What working station are you using?:** Here you should select the computer you are using. This is linked to the GPU name of that system

**Channel containing the nuclear marker for cell detection:** Here you should give the channel of the marker that will be enhanced and should be a number between 1 and 4

**File suffix:** Here select the image file format. Should be .nd2
When this is done a new folder called processed inside your data folder will be created containing the newly generated images. Those will be the ones you will have to import to your Qupath project

## 1. Qupath
### Creating a project
1. Open Qupath
2. File>Project>Create a project in an empty folder
3. Drag your images into Qupath from the processed folder generated in the preceding section.
   
### ROI drawing
4. Use any of the tools to draw your region of interest (ROI) in every image
   
### Particle detection using Stardist
5. Drag and drop to Qupath 202504_Isabel_NuclearClassification\scripts\02_Cell_segmentation.groovy
6. Define all of the parameters for nuclear segmentation as well as the pixel size
   
![image](https://github.com/user-attachments/assets/335ff655-dee7-4ce9-bac7-5b55113514a6)

7. Assign the name of the channels in the image

![image](https://github.com/user-attachments/assets/795678da-cd9c-4617-95cc-a2723c9fec69)

### Train single object classifiers and a composite classifier on the different channels
Don't forget to name the composite classifier "composite".

### Run Groovy Script and export measurements (Qupath)

8. Run the script in the whole project by going to the Script Editor>Run>Run for project

9. When this step is finished export the results by clicking in Measure>Export Measurements

- Be sure to select all of the images you want to include
- Export type should be Image if you want pooled results or Detections if you want a cell per row
- Separator: Tab (.tsv)
