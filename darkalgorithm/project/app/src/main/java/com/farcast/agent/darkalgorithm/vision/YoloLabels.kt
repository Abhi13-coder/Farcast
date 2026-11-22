package com.farcast.agent.darkalgorithm.vision

/**
 * Standard COCO 80-class label order — the class list, not the model's
 * weights, so no licensing question here. This is the "Document its actual
 * training classes and model limitations" requirement from Section 3.C:
 * whatever YOLO export gets loaded, if it's COCO-trained, these are
 * literally the 80 things it can ever say it saw. Anything outside this
 * list (weapons subtypes, specific vehicle makes, faces-as-identity) is NOT
 * something a stock COCO YOLO model detects — don't let a UI imply otherwise.
 */
object YoloLabels {
    val COCO_80 = listOf(
        "person","bicycle","car","motorcycle","airplane","bus","train","truck","boat","traffic light",
        "fire hydrant","stop sign","parking meter","bench","bird","cat","dog","horse","sheep","cow",
        "elephant","bear","zebra","giraffe","backpack","umbrella","handbag","tie","suitcase","frisbee",
        "skis","snowboard","sports ball","kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket","bottle",
        "wine glass","cup","fork","knife","spoon","bowl","banana","apple","sandwich","orange",
        "broccoli","carrot","hot dog","pizza","donut","cake","chair","couch","potted plant","bed",
        "dining table","toilet","tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven",
        "toaster","sink","refrigerator","book","clock","vase","scissors","teddy bear","hair drier","toothbrush"
    )
}
