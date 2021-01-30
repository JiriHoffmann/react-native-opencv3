

// @author Adam G. Freeman - adamgf@gmail.com
#import "FileUtils.h"
#import "MatManager.h"
#import "CvInvoke.h"
#import "CvCamera.h"
#import "RNOpencv3.h"
#import <React/RCTLog.h>


@implementation RNOpencv3

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"onPayload"];
}

-(NSString*) getPartJSON:(cv::Mat&)dFace partKey:(NSString*)partKey part:(cv::Rect)part  {
    
    UIDeviceOrientation deviceOrientation = [[UIDevice currentDevice] orientation];
    NSString* sb = @"";
    
    if (partKey && ![partKey isEqualToString:@""]) {
        NSString *partKeyStr = [NSString stringWithFormat:@",\"%@\":", partKey];
        sb = [sb stringByAppendingString:partKeyStr];
    }
    
    double widthToUse = dFace.cols;
    double heightToUse = dFace.rows;
    
    double X0 = part.tl().x;
    double Y0 = part.tl().y;
    double X1 = part.br().x;
    double Y1 = part.br().y;
    
    double x = X0/widthToUse;
    double y = Y0/heightToUse;
    double w = (X1 - X0)/widthToUse;
    double h = (Y1 - Y0)/heightToUse;
    
    switch(deviceOrientation) {
        case UIDeviceOrientationLandscapeLeft:
            x = 1.0 - Y1/widthToUse;
            y = X0/heightToUse;
            w = (Y1 - Y0)/widthToUse;
            h = (X1 - X0)/heightToUse;
            break;
        case UIDeviceOrientationPortraitUpsideDown:
            x = 1.0 - X1/widthToUse;
            y = 1.0 - Y1/heightToUse;
            break;
        case UIDeviceOrientationLandscapeRight:
            x = Y0/widthToUse;
            y = 1.0 - X1/heightToUse;
            w = (Y1 - Y0)/widthToUse;
            h = (X1 - X0)/heightToUse;
            break;
        default:
        case UIDeviceOrientationPortrait:
            break;
    }
    
    NSString *partStr = [NSString stringWithFormat:@"{\"x\":%f,\"y\":%f,\"width\":%f,\"height\":%f", x, y, w, h];
    sb = [sb stringByAppendingString:partStr];
    
    if (partKey && ![partKey isEqualToString:@""]) {
        sb = [sb stringByAppendingString:@"}"];
    }
    return sb;
}


RCT_EXPORT_METHOD(imageToMat:(NSString*)inPath resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    [FileUtils imageToMat:inPath resolver:resolve rejecter:reject];
}

RCT_EXPORT_METHOD(matToImage:(NSDictionary*)src outPath:(NSString*)outPath resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (outPath == nil || outPath == (NSString*)NSNull.null || [outPath isEqualToString:@""]) {
        return reject(@"EINVAL", [NSString stringWithFormat:@"EINVAL: invalid parameter, param '%@'", outPath], nil);
    }
    
    NSNumber *srcMatNum = [src valueForKey:@"matIndex"];
    int matIndex = (int)[srcMatNum integerValue];
    
    MatWrapper *inputMatWrapper = [((MatManager*)MatManager.sharedMgr).mats objectAtIndex:matIndex];
    //Mat inputMat = [MatManager.sharedMgr matAtIndex:matIndex];
    [FileUtils matToImage:inputMatWrapper outPath:outPath resolver:resolve rejecter:reject];
}

RCT_EXPORT_METHOD(cvtColor:(NSDictionary*)src dstMat:(NSDictionary*)dst convColorCode:(int)convColorCode) {
    
    NSNumber *srcMatNum = [src valueForKey:@"matIndex"];
    NSNumber *dstMatNum = [dst valueForKey:@"matIndex"];
    
    int srcMatIndex = (int)[srcMatNum integerValue];
    int dstMatIndex = (int)[dstMatNum integerValue];
    
    Mat srcMat = [MatManager.sharedMgr matAtIndex:srcMatIndex];
    Mat dstMat = [MatManager.sharedMgr matAtIndex:dstMatIndex];
    
    cvtColor(srcMat, dstMat, convColorCode);
    
    [MatManager.sharedMgr setMat:dstMatIndex matToSet:dstMat];
}


RCT_EXPORT_METHOD(invokeMethods:(NSDictionary*)cvInvokeMap) {
    CvInvoke *invoker = [[CvInvoke alloc] init];
    NSArray *responseArr = [invoker parseInvokeMap:cvInvokeMap];
    NSString *lastCall = invoker.callback;
    int dstMatIndex = invoker.dstMatIndex;
    [self sendCallbackData:responseArr callback:lastCall dstMatIndex:dstMatIndex];
}

// IMPT NOTE: retArr can either be one single array or an array of arrays ...
-(void)sendCallbackData:(NSArray*)retArr callback:(NSString*)callback dstMatIndex:(int)dstMatIndex {
    if (callback != nil && callback != (NSString*)NSNull.null && ![callback isEqualToString:@""]
        && dstMatIndex >= 0 && dstMatIndex < 1000) {
        if (self && retArr && retArr.count > 0) {
            [self sendEventWithName:@"onPayload" body:@{ @"payload" : retArr }];
        }
    }
    else {
        // not necessarily error condition unless dstMatIndex >= 1000
        if (dstMatIndex >= 1000) {
            NSLog(@"Exception thrown attempting to invoke method.  Check your method name and parameters and make sure they are correct.");
        }
    }
}

RCT_EXPORT_METHOD(invokeMethodWithCallback:(NSString*)in func:(NSString*)func params:(NSDictionary*)params out:(NSString*)out callback:(NSString*)callback) {
    CvInvoke *invoker = [[CvInvoke alloc] init];
    int dstMatIndex = [invoker invokeCvMethod:in func:func params:params out:out];
    NSArray *retArr = [MatManager.sharedMgr getMatData:dstMatIndex rownum:0 colnum:0];
    [self sendCallbackData:retArr callback:callback dstMatIndex:dstMatIndex];
}

RCT_EXPORT_METHOD(invokeMethod:(NSString*)func params:(NSDictionary*)params) {
    CvInvoke *invoker = [[CvInvoke alloc] init];
    [invoker invokeCvMethod:nil func:func params:params out:nil];
}

RCT_EXPORT_METHOD(invokeInOutMethod:(NSString*)in func:(NSString*)func params:(NSDictionary*)params out:(NSString*)out) {
    CvInvoke *invoker = [[CvInvoke alloc] init];
    [invoker invokeCvMethod:in func:func params:params out:out];
}

-(void)resolveMatPromise:(int)matIndex rows:(int)rows cols:(int)cols cvtype:(int)cvtype resolver:(RCTPromiseResolveBlock)resolve {
    
    NSDictionary *returnDict;
    if (cvtype == -1) {
        returnDict = @{ @"matIndex" : [NSNumber numberWithInt:matIndex], @"rows" : [NSNumber numberWithInt:rows], @"cols" : [NSNumber numberWithInt:cols] };
    }
    else {
        returnDict = @{ @"matIndex" : [NSNumber numberWithInt:matIndex], @"rows" : [NSNumber numberWithInt:rows], @"cols" : [NSNumber numberWithInt:cols] , @"CvType" : [NSNumber numberWithInt:cvtype] };
    }
    resolve(returnDict);
}

RCT_EXPORT_METHOD(MatWithScalar:(int)rows cols:(int)cols cvtype:(int)cvtype scalarVal:(NSDictionary*)scalarVal resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    int matIndex = [MatManager.sharedMgr createMat:rows cols:cols cvtype:cvtype scalarVal:scalarVal];
    [self resolveMatPromise:matIndex rows:rows cols:cols cvtype:cvtype resolver:resolve];
}

RCT_EXPORT_METHOD(MatWithParams:(int)rows cols:(int)cols cvtype:(int)cvtype resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    int matIndex = [MatManager.sharedMgr createMat:rows cols:cols cvtype:cvtype scalarVal:nil];
    [self resolveMatPromise:matIndex rows:rows cols:cols cvtype:cvtype resolver:resolve];
}

RCT_EXPORT_METHOD(Mat:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    int matIndex = [MatManager.sharedMgr createEmptyMat];
    [self resolveMatPromise:matIndex rows:0 cols:0 cvtype:-1 resolver:resolve];
}

RCT_EXPORT_METHOD(getMatData:(NSDictionary*)mat rownum:(int)rownum colnum:(int)colnum resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSNumber *numForKey = (NSNumber*)[mat objectForKey:@"matIndex"];
    int matIndex = [numForKey intValue];
    NSArray *retArr = [MatManager.sharedMgr getMatData:matIndex rownum:rownum colnum:colnum];
    resolve(retArr);
}

RCT_EXPORT_METHOD(setTo:(NSDictionary*)mat cvscalar:(NSDictionary*)cvscalar) {
    NSNumber *numForKey = (NSNumber*)[mat objectForKey:@"matIndex"];
    int matIndex = [numForKey intValue];
    [MatManager.sharedMgr setToScalar:matIndex cvscalar:cvscalar];
}

RCT_EXPORT_METHOD(put:(NSDictionary*)mat rownum:(int)rownum colnum:(int)colnum data:(NSArray*)data) {
    NSNumber *numForKey = (NSNumber*)[mat objectForKey:@"matIndex"];
    int matIndex = [numForKey intValue];
    [MatManager.sharedMgr putData:matIndex rownum:rownum colnum:colnum data:data];
}

RCT_EXPORT_METHOD(transpose:(NSDictionary*)mat) {
    NSNumber *numForKey = (NSNumber*)[mat objectForKey:@"matIndex"];
    int matIndex = [numForKey intValue];
    [MatManager.sharedMgr transposeMat:matIndex];
}

RCT_EXPORT_METHOD(MatOfInt:(int)lomatval himatval:(int)himatval resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    int matIndex = [MatManager.sharedMgr createMatOfInt:lomatval himatval:himatval];
    resolve(@{ @"matIndex" : [NSNumber numberWithInt:matIndex]});
}

RCT_EXPORT_METHOD(MatOfFloat:(float)lomatval himatval:(float)himatval resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    int matIndex = [MatManager.sharedMgr createMatOfFloat:lomatval himatval:himatval];
    resolve(@{ @"matIndex" : [NSNumber numberWithInt:matIndex]});
}

RCT_EXPORT_METHOD(deleteMat:(NSDictionary*)mat) {
    NSNumber *matIndexNum = [mat valueForKey:@"matIndex"];
    int matIndex = [matIndexNum intValue];
    [MatManager.sharedMgr deleteMatAtIndex:matIndex];
}

RCT_EXPORT_METHOD(deleteMats) {
    [MatManager.sharedMgr deleteMats];
}


RCT_EXPORT_METHOD(useCascadeOnImage:(NSString *)cascadeClassifier src:(NSDictionary*)src resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    RCTLogInfo(@"Pretending to create an event %@", cascadeClassifier);
    
    NSString *cascadePath = [FileUtils loadBundleResource:cascadeClassifier extension:@"xml"];
    RCTLogInfo(@"cascade path %@", cascadePath);

    CascadeClassifier cascade;
    if (cascadePath) {
        RCTLogInfo(@"if cascade path");

        if (!cascade.load( std::string([cascadePath UTF8String]))) {
            NSLog(@"Unable to load cascade classifier at: %@", cascadePath);
        }
        else {
            RCTLogInfo(@"else - cascade loaded");

            NSNumber *srcMatNum = [src valueForKey:@"matIndex"];
            
            int srcMatIndex = (int)[srcMatNum integerValue];
            Mat in = [MatManager.sharedMgr matAtIndex:srcMatIndex];
            
            std::vector<cv::Rect> objects;
            
            cascade.detectMultiScale(in, objects);
            
            NSString *payloadJSON = @"";
            if (objects.size() > 0) {
                payloadJSON = [payloadJSON stringByAppendingString:@"{\"objects\":["];
                for(size_t i = 0; i < objects.size(); i++) {
                    
                    NSString *objectJSON = [self getPartJSON:in partKey:@"" part:objects[i]];
                                          payloadJSON = [payloadJSON stringByAppendingString:objectJSON];
                                          
                                          NSString *objectIdStr = [NSString stringWithFormat:@",\"id\":\"%d\"", (int)i];
                                          payloadJSON = [payloadJSON stringByAppendingString:objectIdStr];
                                          
                                          if (i != (objects.size() - 1)) {
                        payloadJSON = [payloadJSON stringByAppendingString:@"},"];
                    }
                                          else {
                        payloadJSON = [payloadJSON stringByAppendingString:@"}"];
                    }
                }
                payloadJSON = [payloadJSON stringByAppendingString:@"]}"];
                resolve(payloadJSON);
            } else {
                resolve(@"{\"objects\":[]}")
            }
        }
    } else {
        reject(@"Create Event error", @"Cascade file doesn't exist", nil);
    }
    
}


@end
