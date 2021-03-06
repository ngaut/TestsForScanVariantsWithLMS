#include <fcntl.h>
#include <errno.h>
#include <err.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
#include <stdlib.h>
#include <pthread.h>
void Scan(float*);
int main(int argc, char *argv[])
{
  if (argc < 4) {
    printf("Missing arguments. Usage: filename numberOfTuples compareValue numThreads\n");
    return 0;
  }
  printf("Usage: filename numberOfTuples compareValue numThreads\n");
  printf("If the operator is not parallelized, please pass numThreads=0\n");
  printf("If the operator is parallelized and numThreads=0, it will result in errors.\n");
  FILE *ptr_file;
  char buf[1000];
  int numTuples=atoi(argv[2]);
  float compareValue=atof(argv[3]);
  int numThreads=atoi(argv[4]);
  int numReadTuples=0;
  ptr_file =fopen(argv[1],"r");
  if (!ptr_file){
    return 0;
  }
  float *array;
  array=(float*)malloc(((2*numTuples)+3+(2*numThreads))*sizeof(float));
  array[0]=compareValue;
  array[1]=(float)numTuples;
  array[2]=(float)numThreads;
  for (int i=0; i<(2*numThreads); i++){
    array[3+i]=(float)0;
  }
  while (fgets(buf,1000, ptr_file)!=NULL && numReadTuples<numTuples){
    array[numReadTuples+3+(2*numThreads)]=atof(buf);
    numReadTuples++;
  }
  fclose(ptr_file);
  if (numReadTuples<numTuples){
    printf("Error, file contains less tuples than specified.\n");
    return 0;
  }
  Scan(array);
  return 1;
}
/*****************************************
Emitting C Generated Code
*******************************************/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
void Scan(float*  x0) {
  float x8 = x0[2];
  float x5 = x0[1];
  int32_t x7 = x5 / 4;
  float x12 = x7 / x8;
  float x6 = x0[0];
  int32_t x15 = 2 * x8;
  int32_t x16 = 3 + x15;
  int32_t x25 = x16 + 1;
  int32_t x30 = x16 + 2;
  int32_t x35 = x16 + 3;
  bool x49 = x8 > 0;
  int32_t x74 = x7 / x8;
  int32_t x121 = x7 * 4;
  bool x122 = x121 < x5;
  //#Scan Variants- timer goes here
  // generated code for Scan Variants- timer goes here
  int32_t x1 = 0;
  int32_t x2 = 1;
  int32_t x3 = 3;
  int32_t x4 = 0;
  pthread_t threads[(int)x8];
  int *inputArray;
  inputArray=(int*)malloc(x8*sizeof(int));
  void* parallelPrefixSum(void* input){
    int x10=*(int*)input;
    int32_t x17 = x10 * x7;
    int32_t x18 = x17 / x8;
    int32_t x42 = 3 + x10;
    //#parallel prefix sum
    // generated code for parallel prefix sum
    int32_t x11 = 0;
    for(int x14=0; x14 < x12; x14++) {
      int32_t x19 = x14 + x18;
      int32_t x20 = x19 * 4;
      int32_t x21 = x16 + x20;
      float x22 = x0[x21];
      bool x23 = x22 >= x6;
      x11 += x23;
      int32_t x26 = x25 + x20;
      float x27 = x0[x26];
      bool x28 = x27 >= x6;
      x11 += x28;
      int32_t x31 = x30 + x20;
      float x32 = x0[x31];
      bool x33 = x32 >= x6;
      x11 += x33;
      int32_t x36 = x35 + x20;
      float x37 = x0[x36];
      bool x38 = x37 >= x6;
      x11 += x38;
    }
    int32_t x43 = x11;
    x0[x42] = x43;
    //#parallel prefix sum
  }
  for(int x10=0; x10 < x8; x10++) {
	inputArray[x10]=x10;
	pthread_create(&threads[x10], NULL, parallelPrefixSum, (void *)&inputArray[x10]);
  }
  for(int x10=0; x10 < x8; x10++) {
	pthread_join(threads[x10], NULL);
  }
  if (x49) {
    int32_t x50 = 3 + x8;
    x0[x50] = 0;
    for(int x53=1; x53 < x8; x53++) {
      int32_t x54 = 3 + x53;
      int32_t x55 = x54 + x8;
      int32_t x56 = x54 - 1;
      float x57 = x0[x56];
      int32_t x58 = x56 + x8;
      float x59 = x0[x58];
      float x60 = x57 + x59;
      x0[x55] = x60;
    }
    int32_t x64 = x50 - 1;
    float x65 = x0[x64];
    int32_t x66 = x64 + x8;
    float x67 = x0[x66];
    float x68 = x65 + x67;
    x4 = x68;
  } else {
  }
  void* parallelChunk(void* input){
    int x72=*(int*)input;
    int32_t x82 = 3 + x72;
    int32_t x83 = x82 + x8;
    float x84 = x0[x83];
    int32_t x85 = x84 + 3;
    int32_t x86 = x85 + x15;
    int32_t x87 = x86 + x5;
    int32_t x77 = x72 * x7;
    int32_t x78 = x77 / x8;
    //#parallel chunk
    // generated code for parallel chunk
    int32_t x73 = 0;
    for(int x76=0; x76 < x74; x76++) {
      int32_t x88 = x73;
      int32_t x89 = x87 + x88;
      int32_t x79 = x76 + x78;
      int32_t x80 = x79 * 4;
      int32_t x81 = x16 + x80;
      float x90 = x0[x81];
      x0[x89] = x90;
      bool x92 = x90 >= x6;
      x73 += x92;
      int32_t x95 = x73;
      int32_t x96 = x87 + x95;
      int32_t x94 = x25 + x80;
      float x97 = x0[x94];
      x0[x96] = x97;
      bool x99 = x97 >= x6;
      x73 += x99;
      int32_t x102 = x73;
      int32_t x103 = x87 + x102;
      int32_t x101 = x30 + x80;
      float x104 = x0[x101];
      x0[x103] = x104;
      bool x106 = x104 >= x6;
      x73 += x106;
      int32_t x109 = x73;
      int32_t x110 = x87 + x109;
      int32_t x108 = x35 + x80;
      float x111 = x0[x108];
      x0[x110] = x111;
      bool x113 = x111 >= x6;
      x73 += x113;
    }
    //#parallel chunk
  }
  for(int x72=0; x72 < x8; x72++) {
  	pthread_create(&threads[x72], NULL, parallelChunk, (void *)&inputArray[x72]);
  }
  for(int x72=0; x72 < x8; x72++) {
	pthread_join(threads[x72], NULL);
  }
  if (x122) {
    int32_t x123 = x121 + 3;
    int32_t x124 = x123 + x15;
    int32_t x125 = x5 + 3;
    int32_t x126 = x125 + x15;
    for(int x128=x124; x128 < x126; x128++) {
      float x133 = x0[x128];
      bool x135 = x133 >= x6;
      //#run residue instructions after unroll
      // generated code for run residue instructions after unroll
      int32_t x129 = x4;
      int32_t x130 = x129 + x5;
      int32_t x131 = x130 + x15;
      int32_t x132 = x131 + 3;
      x0[x132] = x133;
      x4 += x135;
      //#run residue instructions after unroll
    }
  } else {
  }
  printf("%s\n","Output array: ");
  int32_t x144 = x4;
  for(int x146=0; x146 < x144; x146++) {
    int32_t x147 = x146 + 3;
    int32_t x148 = x147 + x15;
    int32_t x149 = x148 + x5;
    float x150 = x0[x149];
    printf("%f\n",x150);
  }
  bool x154 = x144 == 0;
  if (x154) {
    printf("%s\n","No results found.");
  } else {
  }
  //#Scan Variants- timer goes here
}
/*****************************************
End of C Generated Code
*******************************************/
