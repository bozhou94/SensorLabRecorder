/*
 *  classifier.h
 *  SensingEngine
 *
 *  Created by hong on 11/5/09.
 *  Copyright 2009 __MyCompanyName__. All rights reserved.
 *
 */

#ifndef _cls_H_
#define _cls_H_

#ifdef __cplusplus
extern "C" {
#endif 

void initClassifier(char *filename, char *url);

void delClassifier();

int gaussian(double * features);

int tree(double * feature);

int accTree(double * features);

 #ifdef __cplusplus
 }
 #endif 

#endif
