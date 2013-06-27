/*
 *  tree.c
 *  SensingEngine
 *
 *  Created by hong on 11/5/09.
 *  Copyright 2009 __MyCompanyName__. All rights reserved.
 *
 */
#include <stdlib.h>
#include "classifier.h"
#include <stdio.h>
#include "mvnpdf.h"
#include "model.h"
//#include "cJSON.h"
//#include "comm.h"
#include <assert.h>

#define SAFE_DELETE(a)  if (a)  {     \
									free(a); \
									a = NULL; \
									}
/*
int dim;
double *cycling, *cyclingOthers, *driving, *running, *sitting, *standing, *walking, *walkingOther, *walkingHand;


void extractModel(char *text)
{
	cJSON *root;
	root=cJSON_Parse(text);
	cJSON *format = cJSON_GetObjectItem(root,"actClassifier");
	dim = cJSON_GetObjectItem(format,"DIM")->valueint;
	cJSON *array = cJSON_GetObjectItem(format,"cycling");
	int len = cJSON_GetArraySize(array); 
	cycling = (double *)malloc(len * sizeof(double)); 
        cyclingOthers= (double *)malloc(len * sizeof(double));
	driving = (double *)malloc(len * sizeof(double));
	running = (double *)malloc(len * sizeof(double));
	sitting = (double *)malloc(len * sizeof(double));
	standing = (double *)malloc(len * sizeof(double));
	walking = (double *)malloc(len * sizeof(double));
	walkingOther = (double *)malloc(len * sizeof(double));
	walkingHand = (double *)malloc(len * sizeof(double));

	for(int i = 0; i<len ; i++){
		*(cycling + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}
	
	array = cJSON_GetObjectItem(format,"cyclingOthers");
	for(int i = 0; i<len ; i++){
		*(cyclingOthers + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"driving");
	for(int i = 0; i<len ; i++){
		*(driving + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"running");
	for(int i = 0; i<len ; i++){
		*(running + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"sitting");
	for(int i = 0; i<len ; i++){
		*(sitting + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"walking");
	for(int i = 0; i<len ; i++){
		*(walking + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"standing");
	for(int i = 0; i<len ; i++){
		*(standing + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}

	array = cJSON_GetObjectItem(format,"walkingOther");
	for(int i = 0; i<len ; i++){
		*(walkingOther + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}
	array = cJSON_GetObjectItem(format,"walkingHand");
	for(int i = 0; i<len ; i++){
		*(walkingHand + i) = cJSON_GetArrayItem(array,i)->valuedouble;
	}


	cJSON_Delete(root);
	//printf("%s\n%d\n%d\n%f\n",out,dim,len,v0);
	//free(out);	
}

void initClassifier(char *filename, char *url)
{
	FILE *f=fopen(filename,"rb");
	if(!f){
	printf("config not found, fetch config from server");
	getfile(url,filename);
	f=fopen(filename,"rb");
	}	
	assert(f);
	fseek(f,0,SEEK_END);long len=ftell(f);fseek(f,0,SEEK_SET);
	char *data=malloc(len+1);fread(data,1,len,f);fclose(f);
	extractModel(data);
	free(data);
}

void delClassifier(){
SAFE_DELETE(cycling);
SAFE_DELETE(cyclingOthers);
SAFE_DELETE(driving);
SAFE_DELETE(running);
SAFE_DELETE(sitting);
SAFE_DELETE(standing);
SAFE_DELETE(walking);
SAFE_DELETE(walkingOther);
SAFE_DELETE(walkingHand);
}
*/


int gaussian(double * features){
// you should check the order of features
        double max = 0;
        int index = 0;       
        double likelihood[9];
	
        likelihood[0] = mvnpdf(features,cyclingMean,cyclingInv,cyclingDetLog,DIM);
        likelihood[1] = mvnpdf(features,cyclingOthersMean,cyclingOthersInv,cyclingOthersDetLog,DIM);
        likelihood[2] = mvnpdf(features,drivingMean,drivingInv,drivingDetLog,DIM);
        likelihood[3] = mvnpdf(features,runningMean,runningInv,runningDetLog,DIM);
        likelihood[4] = mvnpdf(features,sittingMean,sittingInv,sittingDetLog,DIM);
        likelihood[5] = mvnpdf(features,standingMean,standingInv,standingDetLog ,DIM);
        likelihood[6] = mvnpdf(features,walkingMean,walkingInv,walkingDetLog ,DIM);
        likelihood[7] = mvnpdf(features,walkingOtherMean,walkingOtherInv,walkingOtherDetLog ,DIM);
        likelihood[8] = mvnpdf(features,walkingHandMean,walkingHandInv,walkingHandDetLog ,DIM);
        //for(int i = 0;i<9;i++) printf("%f,",likelihood[i]);
	//printf("\n");
        
	
	/*
	int inv = dim;
	int det = dim + dim*dim;
        likelihood[0] = mvnpdf(features,cycling,cycling+inv,cycling[det],dim);
        likelihood[1] = mvnpdf(features,cyclingOthers,cyclingOthers+inv,cyclingOthers[det],dim);
        likelihood[2] = mvnpdf(features,driving,driving+inv,driving[det],dim);
        likelihood[3] = mvnpdf(features,running,running+inv,running[det],dim);
        likelihood[4] = mvnpdf(features,sitting,sitting+inv,sitting[det],dim);
        likelihood[5] = mvnpdf(features,standing,standing+inv,standing[det],dim);
        likelihood[6] = mvnpdf(features,walking,walking+inv,walking[det],dim);
        likelihood[7] = mvnpdf(features,walkingOther,walkingOther+inv,walkingOther[det],dim);
        likelihood[8] = mvnpdf(features,walkingHand,walkingHand+inv,walkingHand[det],dim);
        //for(int i = 0;i<9;i++) printf("%f,",likelihood[i]);
	//printf("\n");
	*/

	max = likelihood[0];
        index = 0;
        for(int i = 1;i<9;i++){
            if (likelihood[i] > max){
                max = likelihood[i];
                index = i;
            }
        }
        switch(index){
        case 0:{
            //cout<<"cycling "<<max<<endl;
            //if (max<-45) return -1;
            //else 
		return 2;
            break;}
        case 1:{
            //cout<<"cyclingO "<<max<<endl;
            //if (max<-45) return -1;
            //else 
		return 2;
            break;}
        case 2:{
            //cout<<"driving "<<max<<endl;
            //if (max<-45) return -1;
            //else
            return 4;
            break;}
            
        case 3:{
            //cout<<"running "<<max<<endl;
            //if (max<-70) return -1;
            //else
            return 3;
            break;}
        case 4:{
            //cout<<"sitting "<<max<<endl;
           // if (max<-15) return -1;
           // else
            return 0;
            break;}
        case 5:{
            //cout<<"standing "<<max<<endl;
            //if (max<-50) return -1;
            //else
            return 0;
            break;}
        case 6:{
            //cout<<"walking "<<max<<endl;
           // if (max<-50) return -1;
            //else
            return 1;
            break;}
        case 7:{
            //cout<<"walkingOther "<<max<<endl;
            //if (max<-45) return -1;
            //else
            return 1;
            break;}
        case 8:{
            //cout<<"walkingHand"<<max<<endl;
            //if (max<-50) return -1;
            //else
            return 1;
            break;} 
        default:{   // unknown state
            break;  // do nothing
        }
        } 
        return -1;
}

int tree(double * feature){
	int type = -1;
    double spEntMean = feature[0];
	double spEntv = feature[1];
	double sfMean = feature[2];
	double sfv = feature[3];
	double rolloffMean = feature[4];
	double rolloffVariance = feature[5];
	double centroidMean = feature[6];
	double centroidVariance = feature[7];
	double bandwidthMean = feature[8];
	double bandwidthVariance = feature[9];
	double lowenergy = feature[10];
    double mfcc1 = feature[11];
    double mfcc2 = feature[12];
	double mfcc3 = feature[13];
	double mfcc4 = feature[14];
	double mfcc5 = feature[15];
	double mfcc6 = feature[16];
	double mfcc7 = feature[17];
	double mfcc8 = feature[18];
	double mfcc9 = feature[19];
	double mfcc10 = feature[20];
	double mfcc11 = feature[21];
	double mfcc12 = feature[22];
	double mfcc13 = feature[23];
	/*
	for(int i = 0;i<FEATURESIZE;i++){
		if(DEBUG) printf("%f,",feature[i]);
	}
	if(DEBUG) printf("\n");
	
	printf("----------%f\n",mfcc13);
	*/
	if(spEntMean<=0.251464){
		if(spEntMean<=0.196753){type = 0;
		}else{
			if(rolloffVariance<=918.092105){
				if(mfcc1<=-0.024296){type = 0;
				}else{
					if(centroidVariance<=719.760676){
						if(rolloffVariance<=87.186842){type = 0;
						}else{
							if(centroidMean<=47.763696){type = 0;
							}else{
								if(spEntv<=0.003908){type = 0;
								}else{type = 1;;}
							}
						}
					}else{type = 0;}
				}
			}else{type = 0;}
		}
	}else{
		if(centroidMean<=82.228238){
			if(lowenergy<=0){
				if(rolloffVariance<=169.957895){type = 0;
				}else{
					if(centroidVariance<=41.017823){type = 0;
					}else{
						if(mfcc2<=-0.042565){
							if(mfcc1<=0.072543){type = 0;
							}else{type = 1;;}
						}else{type = 0;}
					}
				}
			}else{
				if(centroidMean<=20.324351){type = 0;
				}else{
					if(lowenergy<=9){
						if(mfcc6<=0.043434){
							if(centroidVariance<=34.666244){
								if(rolloffVariance<=311.2){type = 0;
								}else{type = 1;;}
							}else{
								if(mfcc5<=0.029696){
									if(lowenergy<=1){
										if(rolloffVariance<=128.197368){type = 0;
										}else{type = 1;;}
									}else{type = 1;;}
								}else{
									if(mfcc11<=-0.043327){type = 1;;
									}else{type = 0;}
								}
							}
						}else{
							if(mfcc6<=0.062939){type = 1;;
							}else{type = 0;}
						}
					}else{
						if(spEntv<=0.013754){type = 0;
						}else{
							if(centroidVariance<=548.542269){type = 1;;
							}else{type = 0;}
						}
					}
				}
			}
		}else{
			if(centroidMean<=98.867228){
				if(mfcc9<=0.013487){
					if(bandwidthMean<=48.620809){type = 0;
					}else{
						if(lowenergy<=8){
							if(mfcc1<=-0.041968){type = 0;
							}else{type = 1;;}
						}else{type = 0;}
					}
				}else{type = 0;}
			}else{type = 0;}
		}
	}
	
	
	
	/*
	if(spEntMean<=0.24621){
		if(spEntMean<=0.187285){type = 0;
		}else{
			if(rolloffVariance<=918.618421){
				if(mfcc1<=-0.024301){type = 0;
				}else{
					if(bandwidthMean<=24.712934){type = 0;
					}else{
						if(rolloffMean<=213.5){
							if(spEntv<=0.00282){type = 0;
							}else{
								if(centroidMean<=46.766745){type = 0;
								}else{type = 1;;}
							}
						}else{type = 0;}
					}
				}
			}else{type = 0;}
		}
	}else{
		if(centroidMean<=87.050723){
			if(lowenergy<=10){
				if(lowenergy<=0){
					if(rolloffVariance<=151.221053){type = 0;
					}else{
						if(centroidVariance<=35.789182){type = 0;
						}else{
							if(mfcc5<=0.016894){
								if(mfcc8<=-0.070069){type = 0;
								}else{type = 1;;}
							}else{type = 0;}
						}
					}
				}else{
					if(centroidMean<=20.029775){type = 0;
					}else{
						if(rolloffMean<=218.25){
							if(mfcc6<=0.042206){
								if(sfMean<=0.004135){type = 0;
								}else{type = 1;;}
							}else{
								if(mfcc6<=0.063401){type = 1;;
								}else{type = 0;}
							}
						}else{type = 0;}
					}
				}
			}else{
				if(centroidVariance<=877.113011){
					if(spEntv<=0.012703){type = 0;
					}else{type = 1;;}
				}else{type = 0;}
			}
		}else{
			if(bandwidthMean<=54.320665){type = 0;
			}else{
				if(lowenergy<=8){
					if(sfMean<=0.009661){type = 1;;
					}else{type = 0;}
				}else{type = 0;}
			}
		}
	}
	

	*/
	return type;
}
