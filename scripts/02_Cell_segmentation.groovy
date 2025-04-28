//Parameters
def pixel_size = 0.3312221
def minimum_area = 20
def maximum_area = 180
def minimum_NeuroD2_intensity = 200
def radiusPixels = 5  // Set your desired radius here
def segmentation_channel = 'NeuroD2_processed' //Channel for the segmentation

//Libraries
import qupath.lib.objects.PathObject
import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP
import qupath.lib.measurements.MeasurementList
import qupath.lib.roi.ROIs

/* If you use this in published work, please remember to cite *both*:
 *  - the original StarDist paper (https://doi.org/10.48550/arXiv.1806.03535)
 *  - the original QuPath paper (https://doi.org/10.1038/s41598-017-17204-5)
 */

setImageType('FLUORESCENCE');

//Set pixel width and height
setPixelSizeMicrons(pixel_size, pixel_size)

//Set channel names
setChannelNames(
     'Ctip2',
     'NeuroD2',
     'Satb2',
     'DAPI',
     'NeuroD2_processed'
)

// Define colors for channels
def color1 = getColorRGB(0, 255, 255) // Cyan
def color2 = getColorRGB(255, 0, 255) // Magenta
def color3 = getColorRGB(0, 255, 0) // Green
def color4 = getColorRGB(255, 255, 255) // Gray
def color5 = getColorRGB(255, 0, 255) // Magenta

// Set the colors for the channels
setChannelColors(color1, color2, color3,color4,color5)

removeObjects(getDetectionObjects(), true)

// Update the viewer to reflect changes
getCurrentViewer().repaintEntireImage()

//This function 
def detect_cells( channel, stardist_thresh, feature1_columnName, feature2_columnName, feature1_filter_min, feature1_filter_max, feature2_filter_min ) {
   
    removeObjects(getDetectionObjects(), true) // Clear previous segmentations

    def modelPath = "C:/Users/u0119446/QuPath/v0.5/Stardist/dsb2018_paper.pb"
    
    // Customize how the StarDist detection should be applied
    // Here some reasonable default options are specified
    def stardist = StarDist2D
        .builder(modelPath)
        .channels(channel)            // Extract channel called 'DAPI'
        .normalizePercentiles(1, 99) // Percentile normalization
        .threshold(stardist_thresh)              // Probability (detection) threshold
        .pixelSize(0.25)              // Resolution for detection
        .cellExpansion(0)            // Expand nuclei to approximate cell boundaries
        .measureShape()              // Add shape measurements
        .measureIntensity()          // Add cell measurements (in all compartments)
        .build()
    	
    // Define which objects will be used as the 'parents' for detection
    // Use QP.getAnnotationObjects() if you want to use all annotations, rather than selected objects
    def pathObjects = QP.getSelectedObjects()
    
    // Run detection for the selected objects
    def imageData = QP.getCurrentImageData()
    if (pathObjects.isEmpty()) {
        QP.getLogger().error("No parent objects are selected!")
        return
    }
    
    stardist.detectObjects(imageData, pathObjects)
    stardist.close() // This can help clean up & regain memory
    
    def detections = getDetectionObjects()
    
    def removeDetectionList = []
    for (i=0; i<detections.size() ;i++) {
        detection = detections[i]
        MeasurementList measurementList = detection.getMeasurementList()
        def object_feature1 = measurementList.get(feature1_columnName)
        def object_feature2 = measurementList.get(feature2_columnName)
        
        if ((object_feature1 <= feature1_filter_min) || (object_feature1 >= feature1_filter_max) || (object_feature2 <= feature2_filter_min)) {
           removeDetectionList.add(detection) 
        }
    }
    
    removeObjects(removeDetectionList, true)
    QP.getCurrentHierarchy().fireHierarchyChangedEvent(this) // This forces refresh
    
    // Transform the objects into cells
    def filtered_detections = getDetectionObjects()
    removeObjects(detections - filtered_detections, true) // Only remove the rejected ones
    
    return filtered_detections
}

def genCenterCircle( detections,radiusPixels ) {
    // Create new detection objects with circular ROIs
    def newDetections = getDetectionObjects().collect { detection ->
        def roiOriginal = detection.getROI()
        def cx = roiOriginal.getCentroidX()
        def cy = roiOriginal.getCentroidY()
        def plane = roiOriginal.getImagePlane() // Still required, even for 2D
    
        // Create circular ROI centered on centroid
        def roiCircle = ROIs.createEllipseROI(
            cx - radiusPixels,  // x-coordinate (top-left of bounding box)
            cy - radiusPixels,  // y-coordinate
            radiusPixels * 2,   // width
            radiusPixels * 2,   // height
            plane               // image plane
        )
    
        // Create new detection with original properties
        PathObjects.createDetectionObject(
            roiCircle,
            detection.getPathClass(),
            detection.getMeasurementList()
        )
    }
    
    // Replace existing detections
    clearDetections()
    fireHierarchyUpdate()
    
    return newDetections
}

//Select all annotations
selectAnnotations()

//Segment cell nuclei using neuroD2 marker
def neuroD2_cells = detect_cells( segmentation_channel, 0.4, 'Area '+qupath.lib.common.GeneralTools.micrometerSymbol()+'^2', segmentation_channel + ': Mean',minimum_area, maximum_area, minimum_NeuroD2_intensity )
neuroD2_cells_circles = genCenterCircle(neuroD2_cells,radiusPixels)


addObjects(neuroD2_cells_circles)

//Measure intensity mean at the new ROI for all channels
selectDetections()
runPlugin('qupath.lib.algorithms.IntensityFeaturesPlugin', '{"pixelSizeMicrons":0.5,"region":"ROI","tileSizeMicrons":25.0,"channel1":true,"channel2":true,"channel3":true,"channel4":true,"doMean":true,"doStdDev":false,"doMinMax":false,"doMedian":false,"doHaralick":false}')

//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


//Apply composite classifier
runObjectClassifier("composite");

