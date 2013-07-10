/*
 *  MLToolKit.c
 *  MLToolKit
 *
 *  Created by hong on 2/19/10.
 *  Copyright 2010 __MyCompanyName__. All rights reserved.
 *
 */

#include "MLToolKit.h"
#include <pthread.h>
#include <stdio.h>
#include <assert.h>
#include "features.h"
#include <string.h>
#include "procAcc.h"
#include "procAudio.h"
#include <math.h>
#include <sys/time.h>
#include <unistd.h>
#include <fcntl.h>
#include <semaphore.h>
#include<stdbool.h>
//#include "dsp2.h"
#include <sys/stat.h>
#include "classifier.h"


//#define DEBUGACC
#ifdef DEBUGACC
  #define DEBUGacc( COMMAND ) COMMAND; 
#else
  #define DEBUGacc( COMMAND )
#endif


//#define DEBUGAUDIO
#ifdef DEBUGAUDIO
  #define DEBUGaudio( COMMAND ) COMMAND; 
#else
  #define DEBUGaudio( COMMAND )
#endif


//#define DEBUGWRITEFILE
#ifdef DEBUGWRITEFILE
  #define DebugWriteFile( COMMAND ) COMMAND; 
#else
  #define DebugWriteFile( COMMAND )
#endif

//#define DSP2
#ifdef DSP2
  #define DSP( COMMAND ) COMMAND; 
#else
  #define DSP( COMMAND )
#endif

#define LABELBUF 20
#define DBSIZE 10 //file size 


char* imei;

acc_config   accCfg;
audio_config audioCfg;
loc_config   loc_Cfg;

dataBuffer   acc_buffer;
bool	accOn = false;
// the semaphore implementation is different in Linux and Mac
// the sensing thread use this to wake up the processing thread
sem_t * sem_acc;
// the processing lock
//pthread_mutex_t mutex_Proc_acc;
//int processing_acc;	

dataBuffer   audio_buffer;
bool	audioOn = false;	
sem_t * sem_audio;
// the processing lock
//pthread_mutex_t mutex_Proc_audio;
//int processing_audio;
FILE *fout;

bool	GPSOn = false;	
//database
/*
DSP(
dsp2 *dsp;
dsp2_source *mic;
dsp2_source *accel;
pthread_mutex_t dsp_lock;
)
*/

//the caller should hold the db lock

DSP(
struct timeval db_time; 
pthread_mutex_t insert_label;
char oldDBname[1000]={"\0"};
void newDB(){
	dsp2_close(dsp);
        // Rename the closed file to indicate it is ready for upload
        if(strlen(oldDBname) > 0)
        {
          char newName[1000];
          sprintf(newName, "%sr", oldDBname);
          rename(oldDBname, newName);
        }
	gettimeofday(&db_time, NULL);
	sprintf(oldDBname,"/home/user/MyDocs/%u_%s.db",db_time.tv_sec,imei);
	dsp2_open(oldDBname, &dsp);
	if(accOn) dsp2_register_source(dsp, "accelerometer", accCfg.samplingRate, &accel);
	if(audioOn) dsp2_register_source(dsp, "microphone", audioCfg.samplingRate, &mic);
	// GPS?
}
)

///////////////////////////////////////////////////
//
//accelerometer 
//
//////////////////////////////////////////////////

// acc processing thread
void * processAccelerometerData(void * arg){
	//NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	double data[accCfg.frameLength*3];
	void (*callback)(int);
	while (1) {
		sem_wait(sem_acc);
		DEBUGacc(printf("wake Acc\n"));
		pthread_mutex_lock(&(acc_buffer.read_lock));
		// very rare case, this safe guard the read will not go beyond write.
		if(acc_buffer.read == acc_buffer.write){
			printf("acc nothing to process\n");
			pthread_mutex_unlock(&(acc_buffer.read_lock));
			continue;
		}
		//copy the data from the acc_buffer
		DEBUGacc(printf("acc, process %u\n",acc_buffer.buffers[acc_buffer.read]);)
		memcpy(data, acc_buffer.buffers[acc_buffer.read], sizeof(double)*accCfg.frameLength*3);
		DEBUGacc(printf("processing buffer # %d\n",acc_buffer.read));
		nextRead(&acc_buffer);
		pthread_mutex_unlock(&(acc_buffer.read_lock));
		//start processing
		//pthread_mutex_lock(&mutex_Proc_acc);
		//processing_acc = 1;
		//pthread_mutex_unlock(&mutex_Proc_acc);
		//processing
		
		//debug
		DEBUGacc(
		/*		
		for(int i = 0; i < accCfg.frameLength*3; i++ ){
			*(data+i)=i;
		}*/
		printf("processing, %f,%f,%f\n",mean(data,accCfg.frameLength),mean(data+accCfg.frameLength,accCfg.frameLength),
		mean(data+2*accCfg.frameLength,accCfg.frameLength) );
		/*
		//double spec[accCfg.frameLength/2+1];
		//do fft for x
		//spectrum(data, accCfg.frameLength, spec);
				
		for(int i = 0; i < accCfg.frameLength/2+1; i++ ){
			printf("%f,",spec[i]);
		}
		*/	
		)
                int activity = processAcc(data,accCfg.frameLength,accCfg.samplingRate,accCfg.callBack);
		DSP(
		struct timeval tv; 
		gettimeofday(&tv, NULL);
		pthread_mutex_lock(&dsp_lock);
		int64_t event_id = dsp2_get_event_id(dsp);
		dsp2_source_insert_blob(accel, event_id, (int64_t)tv.tv_sec, 0, data, accCfg.frameLength*3*sizeof(double) );
		pthread_mutex_unlock(&dsp_lock);
		printf("<<<<<<<<<<<<<<<<<<<<<insert accel done\n");
		)
		//printf("activity: %d\n",activity);
		DEBUGacc(printf("-----------------------Acc----sleep-------------------------------\n"));
		//pthread_mutex_lock(&mutex_Proc_acc);
		//processing_acc = 0;
		//pthread_mutex_unlock(&mutex_Proc_acc);
	}
	//[pool release];
	//return NULL;
}

// acc callback
void getAccSample(double x,double y,double z){
	static int count = 0;
	static double *x_data,*y_data,*z_data;
	double* tmp;
	if (0 == count){
		tmp = (double*)acc_buffer.buffers[acc_buffer.write];		
		x_data = tmp; 
		y_data = tmp + accCfg.frameLength;
		z_data = tmp + 2*accCfg.frameLength;
		DEBUGacc(printf("%u,%u,%u\n",x_data,y_data,z_data);)
	}
	normalizedAcc(x,y,z,x_data + count%accCfg.frameLength, y_data + count%accCfg.frameLength, z_data + count%accCfg.frameLength);
	//}
	//printf("count:%d\n",count);
	if (count%accCfg.frameLength == accCfg.frameLength-1){
		DEBUGacc(        	
		struct timeval tv; 
		gettimeofday(&tv, NULL);
		printf("-------------get acc %s",ctime(&tv.tv_sec));   
		printf("acc_buffer #%d\n",acc_buffer.write));
		if(!nextWrite(&acc_buffer)){
			printf("buffer full\n");
		}
		//pthread_mutex_lock(&mutex_Proc_acc);
		//if (0 == processing_acc){
			//memcpy(accSamples,acc_buffer.buffers[acc_buffer.write], 3*accCfg.frameLength*sizeof(double));
			//processing_acc = 1;
			//pthread_mutex_lock(&(acc_buffer.read_lock));
			//setRead(&acc_buffer, acc_buffer.write);
			//pthread_mutex_unlock(&(acc_buffer.read_lock));
			sem_post(sem_acc);
			//}
		//else {
		//	printf("busy skip\n");
		//}
		//pthread_mutex_unlock (&mutex_Proc_acc);

		// if buffer is full, the last sample will be overwirtten
		
		tmp = (double*)acc_buffer.buffers[acc_buffer.write];		
		x_data = tmp; 
		y_data = tmp + accCfg.frameLength;
		z_data = tmp + 2*accCfg.frameLength;
		DEBUGacc(printf("%u,%u,%u\n",x_data,y_data,z_data);)
      DebugWriteFile(
      //if(fout != NULL && count%(32*) == 1)
      { 
        struct timeval tv; 
	gettimeofday(&tv, NULL);
	fprintf(fout,"%u\n",tv.tv_sec);
	printf("write============%u================\n",tv.tv_sec);     
	fflush(fout);
      }
    )	

     }
	count++;
}

///////////////////////////////////////////////////
//
// audio 
//
//////////////////////////////////////////////////

// audio processing thread

void * processAudioData(void * arg){
	short data[audioCfg.frameLength];
	int count = 0;
	int act = -1;
	int save = 1;
	int win = 16*20;
	short buf[audioCfg.frameLength*win];
	short * tmp;
	while (1) {
		sem_wait(sem_audio);
		DEBUGaudio(printf("wake audio\n");)
		pthread_mutex_lock(&(audio_buffer.read_lock));
		// very rare case, this safe guard the read will not go beyond write.		
		if(audio_buffer.read == audio_buffer.write){
			printf("audio nothing to process\n");
			pthread_mutex_unlock(&(audio_buffer.read_lock));
			continue;
		} 
		DEBUGaudio(printf("process audio_buffer #%d\n",audio_buffer.read));
		//copy the data from the audio_buffer				  
		//memcpy(data,audio_buffer.buffers[audio_buffer.read], sizeof(short)*audioCfg.frameLength);  
		/*			
		tmp = (short *)(audio_buffer.buffers[audio_buffer.read]);	
		for(int i = 0; i< audioCfg.frameLength; i ++) {
			data[i] = (double)tmp[i];
		  }
		*/
		memcpy(buf + (count%win)*audioCfg.frameLength, audio_buffer.buffers[audio_buffer.read], sizeof(short)*audioCfg.frameLength);
		//setRead(&audio_buffer, -1);
		nextRead(&audio_buffer);
		pthread_mutex_unlock(&(audio_buffer.read_lock));
		//start processing
		//pthread_mutex_lock(&mutex_Proc_audio);
		//processing_audio = 1;
		//pthread_mutex_unlock(&mutex_Proc_audio);
		//processing
		//debug
		//DEBUGaudio( printf("audio\n");)
		//printf("%d,%d\n",data[0],*(buf + (count%win)*audioCfg.frameLength));
		//processAudio(data, audioCfg.frameLength, audioCfg.callBack);	       				
		act = processAudio(buf + (count%win)*audioCfg.frameLength, audioCfg.frameLength, audioCfg.callBack);
		//processAudio(tmp, audioCfg.frameLength, audioCfg.callBack);
		//processAudio1(tmp,audioCfg.frameLength, audioCfg.callBack);	       		
		if (act == 1) save = 0;
		DSP(
		if (count%win == (win -1) ){
		if (save){		
		struct timeval tv; 
		gettimeofday(&tv, NULL);
		pthread_mutex_lock(&dsp_lock);
		struct stat file_status;
		stat(oldDBname, &file_status);
		printf("%ld", file_status.st_size);
		if ( file_status.st_size/(1024*1024) > DBSIZE){
			printf("----------------new data base ---------------\n");
			newDB();
		}
		int64_t event_id = dsp2_get_event_id(dsp);
		dsp2_source_insert_blob(mic, event_id, (int64_t)tv.tv_sec, 0, buf, sizeof(buf));
		pthread_mutex_unlock(&dsp_lock);
		printf("========inserted audio===========, %d, %d\n", sizeof(buf),sizeof(short));
		} else{
		  printf("==============voice detected, skip insert=================\n");
		}
		save = 1;
		}		
		)
                //int activity = processAudio(data,audioCfg.frameLength,audioCfg.samplingRate);
		//printf("audio called %d\n",count);
		count++;
		DEBUGaudio(printf("sleep audio\n");)
	}
	//[pool release];
	return NULL;
}

void getAudioSample(short * data,int len){
	static int count = 0;
	static short * next;
	//DEBUGaudio( printf("audio: %d\n",10000);)
	//printf("audio: %d\n",10000);
	if (len<audioCfg.frameLength){
		return;
	}	
	if (0 == count){
		next = (short *)audio_buffer.buffers[audio_buffer.write]; 
	}
        DEBUGaudio(printf("write audio_buffer # %d\n",audio_buffer.write));
	memcpy(next,data,audioCfg.frameLength*sizeof(short));
	DEBUGaudio(printf("audio get %d\n",next[511]);)
	if(!nextWrite(&audio_buffer)){
		printf("audio buffer full !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
	}
	sem_post(sem_audio);
	next = (short *)audio_buffer.buffers[audio_buffer.write];
	count++;
}



///////////////////////////////////////////////////
// Utility functions to allow GUI to write a label
///////////////////////////////////////////////////

DSP(
int write_a_inference(uint64_t timestamp, int is_start, const char *label)
{
   int code;
   static int64_t ts[ LABELBUF ];
   static int iss[ LABELBUF ];
   static char labels[ LABELBUF ][100];
   static int count = 0;
   pthread_mutex_lock(&insert_label);
   int pt = count%LABELBUF;
   ts[pt] = timestamp;
   iss[pt] = is_start;
   strcpy(labels[pt],label);
   //printf("label : %s,%s\n",label,labels[pt]);		
   if(pt == LABELBUF -1){
   DSP(
   pthread_mutex_lock(&dsp_lock);
   for(int i = 0; i < LABELBUF; i++){
   code = dsp2_add_inference(dsp, ts[i], iss[i], labels[i]);
	//printf("insert %lld,%d,%s\n",ts[i],iss[i], labels[i]);
   }		
   pthread_mutex_unlock(&dsp_lock);
	printf("insert inferenecs <<<<<<<<<<<<<<<<<<<\n");
   )
   }	
   count++;
   pthread_mutex_unlock(&insert_label);
   return(code);
}

int write_a_label(uint64_t timestamp, int is_start, int type,const char *label){
   int code;
   DSP(
   pthread_mutex_lock(&dsp_lock);
   code = dsp2_add_label(dsp, timestamp, is_start, type, label);
	//printf("insert %lld,%d,%s\n",ts[i],iss[i], labels[i]);
   pthread_mutex_unlock(&dsp_lock);
   printf("insert labels <<<<<<<<<<<<<<<<<<<\n");
   )
   return code;	
}
)

///////////////////////////////////////////////////
// init & clean up 
//////////////////////////////////////////////////
void init(acc_config * acc_Cfg, audio_config * audio_Cfg, loc_config * loc_Cfg){
	pthread_attr_t  attr;
	pthread_t       accThread;
	pthread_t       audioThread;
	int             returnVal;
	//imei = getIMEIstring();// Call to get IMEI string
	//printf("IMEI << %s >>\n",imei);
	//returnVal = sem_init(&sem_acc, 0, 1);	
	struct timeval tv; 
	gettimeofday(&tv, NULL);
	DSP(
	pthread_mutex_init(&insert_label,NULL);
	gettimeofday(&db_time, NULL);
	sprintf(oldDBname,"/home/user/MyDocs/%u_%s.db",db_time.tv_sec,imei);
	dsp2_open(oldDBname, &dsp);
	pthread_mutex_init(&(dsp_lock), NULL);
	)	
	//initClassifier("/home/user/MyDocs/model.json","http://metro2.cs.dartmouth.edu/model.json");
	printf("load Models....................................\n");
	if (acc_Cfg != NULL){
	accOn = true;
	//copy acc config
	accCfg.samplingRate = acc_Cfg->samplingRate;
	accCfg.frameLength = acc_Cfg->frameLength;
	accCfg.callBack = acc_Cfg->callBack;
	//setup threading	
	initBuffer(&acc_buffer,3*accCfg.frameLength*sizeof(double),15);
	char tmp2[1000];
	sprintf(tmp2,"sem_acc_%u",tv.tv_sec);
	sem_acc = sem_open(tmp2, O_CREAT, S_IRWXU, 0);
	assert(sem_acc != SEM_FAILED);
	//returnVal =  pthread_mutex_init(&mutex_Proc_acc, NULL);
	//assert(!returnVal);
	//printf("%u,%u\n",returnVal,&sem_acc);
	//printf( "Error: %s\n", strerror( errno ) );
	//returnVal = sem_open("sem_acc", O_CREAT, 0777, 0);
	//assert(returnVal != SEM_FAILED);
	//assert(!returnVal);
	returnVal = pthread_attr_init(&attr);
	assert(!returnVal);
	returnVal = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	assert(!returnVal);
	returnVal = pthread_create(&accThread, &attr, &processAccelerometerData, NULL);
	assert(!returnVal);
	returnVal = pthread_attr_destroy(&attr);
	assert(!returnVal);
	//processing_acc = 0;
	DSP(
	dsp2_register_source(dsp, "accelerometer", accCfg.samplingRate, &accel);
	)	
	}
	printf("ACC Init done \n");
	if (audio_Cfg != NULL){
	audioOn = true;
	//copy audio config
	audioCfg.samplingRate = audio_Cfg->samplingRate;
	audioCfg.frameLength = audio_Cfg->frameLength;
	audioCfg.windowLength = audio_Cfg->windowLength;
	audioCfg.mfccLength = audio_Cfg->mfccLength;
	audioCfg.callBack = audio_Cfg->callBack;
	initAudioProcessor(audioCfg.frameLength, audioCfg.windowLength, audioCfg.mfccLength,audioCfg.samplingRate);
	printf("processor Init done \n");
	initBuffer(&audio_buffer,audioCfg.frameLength*sizeof(short),320);
	printf("buf Init done \n");
	//setup threading	
	char tmp3[1000];
	sprintf(tmp3,"sem_audio_%u",tv.tv_sec);
	sem_audio = sem_open(tmp3, O_CREAT, S_IRWXU, 0);
	assert(sem_audio != SEM_FAILED);
	//returnVal =  pthread_mutex_init(&mutex_Proc_audio, NULL);
	//assert(!returnVal);
	//printf("%u,%u\n",returnVal,&sem_audio);
	//printf( "Error: %s\n", strerror( errno ) );
	//returnVal = sem_open("sem_audio", O_CREAT, 0777, 0);
	//assert(returnVal != SEM_FAILED);
	//assert(!returnVal);
	returnVal = pthread_attr_init(&attr);
	assert(!returnVal);
	returnVal = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	assert(!returnVal);
	returnVal = pthread_create(&audioThread, &attr, &processAudioData, NULL);
	assert(!returnVal);
	returnVal = pthread_attr_destroy(&attr);
	assert(!returnVal);
	//processing_audio = 0;
	DSP(	
	dsp2_register_source(dsp, "microphone", audioCfg.samplingRate, &mic);
	)	
	}

        DebugWriteFile(
        fout = fopen("/home/user/MyDocs/test.log", "w");
	//fprintf(fout,"start\n");
	//fflush(fout);
        if(fout == NULL)
        {
           perror("\nError, can't open /home/user/MyDocs/sensor.log for writing.");
        }
        )
	printf("Init done \n");

}

void destroy(){
	//need more stuff here, stop the treads
	if (accOn){ 
	  delBuffer(&acc_buffer);
	  //sem_destroy(&sem_acc);
	  sem_close(sem_acc);
	  //pthread_mutex_destroy(&mutex_Proc_acc);
	}

	if (audioOn){ 
	  delBuffer(&audio_buffer);
	  //sem_destroy(&sem_acc);
	  sem_close(sem_audio);
	  delAudioProcessor();
	  //pthread_mutex_destroy(&mutex_Proc_audio);
	}

	//delClassifier();

	DSP(
	dsp2_close(dsp);
	pthread_mutex_destroy(&insert_label);
	pthread_mutex_destroy(&dsp_lock);
	)

}

