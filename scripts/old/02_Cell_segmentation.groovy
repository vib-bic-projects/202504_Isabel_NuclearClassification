//Parameters
def minimum_area = 20
def maximum_area = 180
def minimum_Satb2_intensity = 350
def minimum_NeuroD2_intensity = 450
def minimum_Ctip2_intensity = 600 
def overlapThreshold = 0.2 // Minimum fractional overlap (based on smallest object) to consider two detections as overlapping

//Libraries
import qupath.lib.objects.PathObject
import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP
import qupath.lib.measurements.MeasurementList

/* If you use this in published work, please remember to cite *both*:
 *  - the original StarDist paper (https://doi.org/10.48550/arXiv.1806.03535)
 *  - the original QuPath paper (https://doi.org/10.1038/s41598-017-17204-5)
 */

setImageType('FLUORESCENCE');

//Set channel names
setChannelNames(
     'Ctip2',
     'NeuroD2',
     'Satb2',
     'DAPI'
)

// Define colors for channels
def color1 = getColorRGB(0, 255, 255) // Cyan
def color2 = getColorRGB(255, 0, 255) // Magenta
def color3 = getColorRGB(0, 255, 0) // Green
def color4 = getColorRGB(255, 255, 255) // Gray

// Set the colors for the channels
setChannelColors(color1, color2, color3,color4)

removeObjects(getDetectionObjects(), true)

// Update the viewer to reflect changes
getCurrentViewer().repaintEntireImage()

//This function 
def detect_cells( channel, stardist_thresh, feature1_columnName, feature2_columnName, feature1_filter_min, feature1_filter_max, feature2_filter_min ){

    
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

// ====== HELPER FUNCTION: Compute fractional overlap between two detections ======
//Instead of using getshape use the geometry instead of the shape
//Perform tests to see what it does between two detections



def getOverlapFraction(d1, d2) {
    def geom1 = d1.getROI().getGeometry()
    def geom2 = d2.getROI().getGeometry()
    
    //unionGeometry = geom1.union(geom2)
    unionGeometry = geom1.intersection(geom2)
    /*
    def area1 = new java.awt.geom.Area(geom1)
    def area2 = new java.awt.geom.Area(geom2)

    def intersection = new java.awt.geom.Area(area1)
    intersection.intersect(area2)

    def intersectArea = intersection.getBounds2D().width * intersection.getBounds2D().height
    def area1Size = area1.getBounds2D().width * area1.getBounds2D().height
    def area2Size = area2.getBounds2D().width * area2.getBounds2D().height

    return intersectArea / Math.min(area1Size, area2Size)
    */
    
    //print(unionGeometry.getArea() / Math.max(geom1.getArea(), geom2.getArea()))
    
    return unionGeometry.getArea() / Math.min(geom1.getArea(), geom2.getArea())
}

// Compare detections from two channels still need to work on a solution that works here
def filter_duplicated_cells (overlapThreshold, allDetections){
    
    // ====== STEP 1: Define lists for objects to keep and to remove ======
    def toKeep = []   // List to store detections we want to keep
    def toRemove = [] // List to store redundant overlapping detections to remove
    
    // ====== STEP 2: Create a mutable copy of all detections to process ======
    def unprocessed = new ArrayList<>(allDetections) // Copy of all detections that we’ll loop through
    
    // ====== STEP 3: Loop over detections and group overlaps ======
    while (!unprocessed.isEmpty()) {
        def current = unprocessed.remove(0) // Take the first detection from the list
        def overlapping = [current]         // Start a group of overlapping detections with just this one
    
        def overlapsFound = []              // Store which other detections overlap with the current one
    
        // Compare current detection with all remaining ones
        for (other in unprocessed) {
            def overlap = getOverlapFraction(current, other) // Compute how much they overlap
            print(overlap)
            print(overlapThreshold)
            if (overlap > overlapThreshold) {
                overlapping << other        // Add to overlap group
                overlapsFound << other      // Mark as found so we can remove them later
            }
        }
    
        // Remove those that were grouped from the list so we don’t process them again
        unprocessed.removeAll(overlapsFound)
        overlapping << current 
        // If the group contains more than one overlapping detection
        if (overlapping.size() > 1) {
            //def largest = overlapping.max { it.getROI().getArea() } // Find the largest detection by actual area
            def overlapping_sorted = overlapping.sort{ it.getROI().getArea() }
            toKeep.add(overlapping_sorted[0])
//            toKeep << largest                                        // Keep the largest one
            toRemove.addAll(overlapping.findAll { it != overlapping_sorted[0] })  // Remove all others in the group
        } else {
            // If no overlaps, just keep the original detection
            toKeep << current
        }
    }
    
    // ====== STEP 4: Apply changes ======
    removeObjects(allDetections, true) // Remove all overlapping detections that are not the largest
    fireHierarchyUpdate()         // Refresh QuPath’s object hierarchy so it displays correctly
    
    // ====== Done ======
    print "Merged segmentations: Kept ${toKeep.size()} nuclei, removed ${toRemove.size()} overlapping duplicates."
    
    return toKeep
}

// Segment cells and put them in the same list and add them as cells

def pooled = []


//def ctip2_cells = detect_cells( 'Ctip2', 0.4, 'Area '+qupath.lib.common.GeneralTools.micrometerSymbol()+'^2', 'Ctip2: Mean',minimum_area, maximum_area, minimum_Ctip2_intensity )
//removeObjects(getDetectionObjects(), true) // Clear previous segmentations


def neuroD2_cells = detect_cells( 'NeuroD2', 0.4, 'Area '+qupath.lib.common.GeneralTools.micrometerSymbol()+'^2', 'NeuroD2: Mean',minimum_area, maximum_area, minimum_NeuroD2_intensity )
removeObjects(getDetectionObjects(), true) // Clear previous segmentations
addObjects(neuroD2_cells)

//def satb2_cells = detect_cells( 'Satb2', 0.5, 'Area '+qupath.lib.common.GeneralTools.micrometerSymbol()+'^2', 'Satb2: Mean',minimum_area, maximum_area, minimum_Satb2_intensity )
//removeObjects(getDetectionObjects(), true) // Clear previous segmentations

// Combine all detections into a single list
def combinedListofObjects = []
combinedListofObjects.addAll(ctip2_cells)
combinedListofObjects.addAll(neuroD2_cells)
combinedListofObjects.addAll(satb2_cells)

def cellstoKeep = filter_duplicated_cells (overlapThreshold, combinedListofObjects)

addObjects(combinedListofObjects)
//def cells = CellTools.detectionsToCells(ctip2_cells, 0, 0)
//addObjects(cells)

