/*******************************************************************************************************************************************
ScalaVariantTests class, defining some initial tests for developing a code-generating scheme for scan variants,
based on EPFL's Light-weight modular staging library and a slightly modified version of their DSL api for 
C-code generation.

Author: 
Gabriel Campero
gabrielcampero@acm.org


Used library based on LMS source code by Tiark Rompf and others, retrieved on December, 2014, from: http://scala-lms.github.io/
References used: 
Klonatos, Yannis, et al. "Legobase: Building efficient query engines in a high-level language, 2014."

OvGU, January-March 2015.
*********************************************************************************************************************************************
More information on LMS:
Lightweight Modular Staging (LMS) is a runtime code generation approach. 
The framework provides a library of core components for building high performance code generators and embedded compilers in Scala.

LMS is used by several other projects, including:

Delite: domain-specific languages for heterogeneous parallel computing.
Spiral: library generators for high-performance numerical kernels.
LegoBase: query compilation in database systems.

LMS is developed and used by researchers and practitioners from EPFL, Stanford, ETH, Purdue, University of Rennes, Oracle Labs, Huawei Labs, 
and other institutions.

*********************************************************************************************************************************************
Notes on variants applied:

	BRANCH REMOVAL: Only one call of remove branching is effectful. More calls dont add anything new to the execution.
	A scheme for branch removal only on selected threads could be supported, but we're not sure on the uses/benefits of this.

	LOOP UNROLLING: Loop unrolling can be added up. If there is an attempt to use an unrolled loop beyond, but passing less tuples
        than there are instructions in one unrolled iteraton, then the whole loop will run regularly in a section defined as the residue of the unrolling. 

	PARALLELIZATION: As it is implemented, only one parallelization is defined, dividing the iterations among them. 
	Number of threads will be passed as parameter at run time.
	Adding more threads than there are iterations will have no effect, and the loop will run unparallelized in the section defined as the residue
        of the unrolling.
	Later more types of parallelization could be explored, if there is an interest for this. 
	
	VECTORIZATION:?

*********************************************************************************************************************************************
Working Notes:

Some ideas for parallelization can be found below, as comments to the code. 

Ideas for vectorization: ?

If not possible to generate the code for parallelization or vectorization with LMS, perhaps it could be added by getting LMS to output some
special strings to mark where parallelization should be inserted, and then post-processing the generated code with text insertion and deletion. 
If this is accomplished, then it could be further wrapped in an outer Scala class, so as to have a single call to the code-generator, and it's use 
of processing and post-processeing wouldn't be noticed by the final system.


*********************************************************************************************************************************************
Future work:
1) Embed test in cleaner function, taking input from console, so as to be usable by an existing system.
2) Design and apply a clear scheme for naming the generated codes according to variants used, or to other requirements from existing system.
3) Create copies of this that generate code for vectors of ints and other types of data.
------------------------------

*/

package scala.lms.scan_variants

import scala.virtualization.lms.common._
import scala.io.Source
import java.io.PrintWriter
import java.io.File


class ScanVariantsTests extends TutorialFunSuite {

  val under = "c_code_generation_tests_"

  test("ScanVariants") {
  
   /**Future work: This should be embedded in a cleaner function, taking input from console, so as to be usable by an existing system. **/
  
   /*Configuration for the variants to be applied*/
   var numInstructionsForUnrolling: Int=4;
   var instructions = new Array[String](numInstructionsForUnrolling); 
   instructions(0) = "_";//Remove branching  
   instructions(1) = "Unroll"; //"Unroll"; 
   instructions(2) = "_";//"Parallelize"; 
   instructions(3) = "_";//"Vectorize";
   var unrollDepth: Int=5
   var vectorSize: Int=4

   /*Configuration for the predicate*/
   val equals = false;
   val greaterThan=false;
   val greaterThanEquals=false;
   val lesserThan=false;
   val lesserThanEquals=true;
   val notEqual=false;

   var predicate: String ="scan_all";

   if (equals){
	predicate="equals";
   }
   else if (greaterThan){
	predicate="greaterThan";
   }
   else if (greaterThanEquals){
	predicate="greaterThanEquals";
   }
   else if (lesserThan){
	predicate="lesserThan";
   }
   else if (lesserThanEquals){
	predicate="lesserThanEquals";
   }
   else if (notEqual) {
        predicate="notEquals";
   }
   else {
        predicate="scan_all";
   }


   /*Definition of the snippet for code-generation, using a slightly modified version of EPFL's LMS DSL api for C-code generation*/		
   val snippet = new DslDriverC[Array[Float],Unit] {

	/*Area where context is shared between 2 stages: the code-generation stage and the execution stage*/


	/*Snippet function defining the function whose code is to be generated
	Takes as input an array of floats. By convention, the first parameter is the value for comparison, the second parameter is the size of the input array, 
	the following parameters are the input array and then there is a set of empty records of the same size as the input array, for storing the results...**/

	def snippet(input: Rep[Array[Float]]) = comment("Scan Variants- timer goes here", verbose = true) {

		/*Input values*/
  		lazy val valueForComparison:Rep[Float]=input(0); 
		lazy val maxNumIt:Rep[Int]=input(1).asInstanceOf[this.Rep[Int]];
		lazy val numThreadsSelected:Rep[Int]=input(2).asInstanceOf[this.Rep[Int]];
	
		/*Local context variables*/
		var zero: this.Variable[Int]=0 //Definition of number 0, for typing purposes.
		var one: this.Variable[Int]=1 //Definition of number 1, for typing purposes.
		var three: this.Variable[Int]=3 //Definition of number 3, for typing purposes.
		var outputPos: this.Variable[Int]=0 //Definition of the output position, relative to number of hits.
	

		/*For ease of work, and after observing some unexpected typing issues (and forward references problems), we decided to place here the definition of 
		the loop classes, in charge of handling the application of the variants.*/

		/**Definition of the Abstract Loop Representation class, in charge of handling the application of the variants. */
		abstract class AbstractLoopRepresentation {
			/*Inner members*/
			var numIterations: Exp[Any] =_; //Number of interations of original loop
			var numIterationsUnrolled: Rep[Int] =_; //Number of Iterations of unrolled loop
			var numThreads: Rep[Int] =_; //Number of threads
			var value: Exp[Any] =_; //Value for comparison
			var predicateType: String=_;//Predicate type
			var numInst: Int =_; //Number of instructions per iteration. 1 by default.
			var bfc: Boolean=_;//Flag to define if using branch-free-code. False by default.
			var unrolled: Boolean=_;//Flag to define if loop has been unrolled. False by default.
			var parallelized: Boolean=_;//Flag to define if loop has been parallelized. False by default.
			var threadsDisplacement: Rep[Int] =_;

			/*Parametric constructor. Takes as input the number of iterations, compare value and predictate.*/
			def this (numIt: Rep[Int], compareValue: Rep[Float], pred:String){
				this();
				numIterations=numIt;
				numIterationsUnrolled=numIt;
				value=compareValue;
				predicateType=pred;
				numThreads=varIntToRepInt(one);
				threadsDisplacement=varIntToRepInt(zero);
				numInst=1;
				bfc=false;
				unrolled=false;
				parallelized=false;

			}

			/*Some getters...*/
			def getPredicateType():String={
				predicateType
			}
			def getNumIterations():Rep[Int]={
				numIterationsUnrolled
			}
			def getNumThreads():Rep[Int]={
				numThreads
			}
			def getThreadsDisplacement():Rep[Int]={
				threadsDisplacement
			}
			def getNumInstructionsPerUnrolledIteration(): Int={
				numInst
			}
			def isUnrolled():Boolean={
				unrolled
			}
			def isParallelized():Boolean={
				parallelized
			}

			/*Function in charge of applying variant changes to current loop configuration.*/
			def applyVariant(instruction: String){
				if (instruction=="Remove branching"){
					bfc=true;
				}
				else if (instruction=="Unroll"){
					unrolled=true;
					numIterationsUnrolled=(numIterationsUnrolled/unrollDepth).asInstanceOf[Rep[Int]]
					numInst=(numInst*(unrollDepth.asInstanceOf[Int])); 
					/*TODO Check: The number of instructions only changes if there is more than one 		
					iteration on the remaining loop.*/
				}
				else if (instruction=="Parallelize"){
					parallelized=true;
					numThreads=numThreadsSelected
					threadsDisplacement=numThreadsSelected
				}
			}

			/*runLoop: Function responsible for running the loop. 
			Parallelization changes can be handled from here.*/
			def runLoop(){

				if (!parallelized){
					/*Loop for code generation*/
			        	for (i <- (0 until numIterationsUnrolled): Rep[Range]) {
			        	  this.runInstructionOfIteration(i)
			        	}
				}
				else{
					//Parallel prefix sum...
					for (j <- (0 until numThreads): Rep[Range]) {
						this.runParallelPrefixSum(j)//Should be done in parallel
					}

					//Serial assignment of output positions...
/*List of limitations observed:
Its not possible to do multiple assignments to a position in an Array of Rep: they dont show.
We cannot use a Rep as a counter, because it doesnt get processed in the end
We cannot cast a RepInt to a VarInt, but the other way around is ok.
we can only assign a value to a RepInt.  
*/					
					if (threadsDisplacement>0){
						input(3+threadsDisplacement)=varIntToRepInt(zero).asInstanceOf[Rep[Float]];
						for (k <- (1 until numThreads): Rep[Range]) {
							input(3+k+threadsDisplacement)=input(3+k-1)+input(3+k-1+threadsDisplacement)
						}
						outputPos=input(3+numThreads-1)+input(3+numThreads-1+threadsDisplacement);
					}

					for (l <- (0 until numThreads): Rep[Range]) {
						this.runParallelChunk(l)//Should be done in parallel
					}
					//Parallel writing...					

				}

				/*Code generation for a non-optimized loop with the residual iterations after incomplete parallelization*/
		//		if (this.parallelized){
		//		        	if(((numIterationsUnrolled/numThreads).asInstanceOf[Rep[Int]]*numInst*numThreads)<maxNumIt){
		//					for (i <- ( (((numIterationsUnrolled/numThreads).asInstanceOf[Rep[Int]]*numInst*numThreads)+3+(2*threadsDisplacement)) until maxNumIt+3+(2*threadsDisplacement)): Rep[Range]) {
		  //      			  		this.runInstructionUnrollResidue(i)//Note the invocation to the residual instruction
        		//			}
			//		}		
			//	}
				/*Code generation for a non-optimized loop with the residual iterations after unrolling*/
				if (this.unrolled){
				        	if((numIterationsUnrolled*numInst)<maxNumIt){
							for (i <- ( ((numIterationsUnrolled*numInst)+3+(2*threadsDisplacement)) until maxNumIt+3+(2*threadsDisplacement)): Rep[Range]) {
		        			  		this.runInstructionUnrollResidue(i)//Note the invocation to the residual instruction
        					}
					}		
				}

			}


			/*Function that counts the outputs of a thread.
			Takes as input the thread number.
			It handles mapping from thread number to iteration number to input & output arrays, considering variants performed.*/
			def runParallelPrefixSum(it: Rep[Int])= comment("parallel prefix sum", verbose = true){
				var count: Variable[Int] = 0;
				for (i <- (0 until (numIterationsUnrolled.asInstanceOf[Rep[Float]]/(numThreads).asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]]): Rep[Range]) {
					var currInst:Int=0;
					while (currInst<numInst){
						var itVal:Rep[Int]=3+(2*threadsDisplacement);
						itVal+=currInst;
						itVal+=((i+(it*numIterationsUnrolled/numThreads))*numInst)
						if (bfc){
							if (predicate=="equals"){
									//println(input(itVal))
									count+=(input(itVal)==value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]]
							}
   							else if (predicate=="greaterThan"){
									count+=(input(itVal)>value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   							}	
							else if (predicate=="greaterThanEquals"){
									count+=(input(itVal)>=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   							}
							else if (predicate=="lesserThan"){
									count+=(input(itVal)<value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];  	   								}
							else if (predicate=="lesserThanEquals"){
									count+=(input(itVal)<=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];	
		   					}
							else if (predicate=="notEquals"){
									count+=(input(itVal)!=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
		      					}
			   				else {
									count+=1;
							}
						}
						else if (predicate=="equals"){
							if (input(itVal)==value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
						}
   						else if (predicate=="greaterThan"){
							if (input(itVal)>value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
   						}	
						else if (predicate=="greaterThanEquals"){
							if (input(itVal)>=value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
   						}
						else if (predicate=="lesserThan"){
							if (input(itVal)<value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
   						}	
						else if (predicate=="lesserThanEquals"){
							if (input(itVal)<=value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
   						}
						else if (predicate=="notEquals"){
							if (input(itVal)!=value.asInstanceOf[Rep[Float]]){
								count+=1;				
							}
   						}
   						else {
								count+=1;				
						}
				
						currInst=currInst+1;
					} //End of while loop
				}//End of for loop
				input(3+it)=varIntToRepInt(count).asInstanceOf[Rep[Float]]; 		
			}//End of def runParallelPrefixSum


			/*Function that counts the outputs of a thread.
			Takes as input the thread number.
			It handles mapping from thread number to iteration number to input & output arrays, considering variants performed.*/
			def runParallelChunk(it: Rep[Int])= comment("parallel chunk", verbose = true){
				var count: Variable[Int] = 0;
				for (i <- (0 until numIterationsUnrolled/numThreads): Rep[Range]) {
					var currInst:Int=0;
					while (currInst<numInst){
						var itVal:Rep[Int]=3+(2*threadsDisplacement);
						itVal+=currInst;
						itVal+=((i+(it*numIterationsUnrolled/numThreads))*numInst)
						if (bfc){
							if (predicate=="equals"){
									//println(input(itVal))
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
									count+=(input(itVal)==value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
							}
   							else if (predicate=="greaterThan"){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=(input(itVal)>value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   							}	
							else if (predicate=="greaterThanEquals"){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
	
								count+=(input(itVal)>=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   							}
							else if (predicate=="lesserThan"){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=(input(itVal)<value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];  	   								}
							else if (predicate=="lesserThanEquals"){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=(input(itVal)<=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];	
		   					}
							else if (predicate=="notEquals"){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=(input(itVal)!=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
		      					}
			   				else {
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;
							}
						}
						else if (predicate=="equals"){
							if (input(itVal)==value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;
							}
						}
   						else if (predicate=="greaterThan"){
							if (input(itVal)>value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;
							}
   						}	
						else if (predicate=="greaterThanEquals"){
							if (input(itVal)>=value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;
							}
   						}
						else if (predicate=="lesserThan"){
							if (input(itVal)<value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;
							}
   						}	
						else if (predicate=="lesserThanEquals"){
							if (input(itVal)<=value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;					
							}
   						}
						else if (predicate=="notEquals"){
							if (input(itVal)!=value.asInstanceOf[Rep[Float]]){
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;							
							}
   						}
   						else {
								input(input(3+it+threadsDisplacement).asInstanceOf[Rep[Int]]+3+(2*threadsDisplacement)+maxNumIt+varIntToRepInt(count))=input(itVal);
								count+=1;						
						}
				
						currInst=currInst+1;
					} //End of while loop
				}//End of for loop		
			}//End of def runParallelChunk

			/*Function that defines the series of instructions to be executed in one iteration of the resulting
			loop.
			Takes as input the iteration number.
			It handles mapping from iteration number to input & output arrays, considering variants performed.*/
			def runInstructionOfIteration(it: Rep[Int])= comment("run instruction", verbose = true){

				var currInst:Int=0;
				while (currInst<numInst){
					var itVal:Rep[Int]=((it*numInst)+currInst)+3+(2*threadsDisplacement)
					if (bfc){
						if (predicate=="equals"){
								//println(input(itVal))
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
								outputPos+=(input(itVal)==value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
						}
   						else if (predicate=="greaterThan"){
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
				                		outputPos+=(input(itVal)>value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   						}	
						else if (predicate=="greaterThanEquals"){
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
				                		outputPos+=(input(itVal)>=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   						}
						else if (predicate=="lesserThan"){
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
				                		outputPos+=(input(itVal)<value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];   		   							}
						else if (predicate=="lesserThanEquals"){
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
				                		outputPos+=(input(itVal)<=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];	
	   					}
						else if (predicate=="notEquals"){
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
				                		outputPos+=(input(itVal)!=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
	      					}
		   				else {
								input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
								outputPos+=1;
						}
					}
					else if (predicate=="equals"){
						if (input(itVal)==value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;
						}
					}
   					else if (predicate=="greaterThan"){
						if (input(itVal)>value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;
						}
   					}	
					else if (predicate=="greaterThanEquals"){
						if (input(itVal)>=value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;
						}
   					}
					else if (predicate=="lesserThan"){
						if (input(itVal)<value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;					
						}
   					}	
					else if (predicate=="lesserThanEquals"){
						if (input(itVal)<=value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;					
						}
   					}
					else if (predicate=="notEquals"){
						if (input(itVal)!=value.asInstanceOf[Rep[Float]]){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;
						}
   					}
   					else {
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;
					}
				
					currInst=currInst+1;
				} //End of while loop		
			}//End of def runInstructionOfIteration

			/*Function that defines the instructions to be executed for the residue left after applying unrolling, without
			uneven instructions per iteration. 
			Takes as input the absolute positions of tuples not visited.
			It handles mapping from iteration number to input & output arrays, considering variants performed.*/
			def runInstructionUnrollResidue(itVal: Rep[Int])= comment("run residue instructions after unroll", verbose = true){
				if (bfc){
					if (predicate=="equals"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=(input(itVal)==value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
					}
   					else if (predicate=="greaterThan"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
			                		outputPos+=(input(itVal)>value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
   					}	
					else if (predicate=="greaterThanEquals"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
			                		outputPos+=(input(itVal)>=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
  					}
					else if (predicate=="lesserThan"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
			                		outputPos+=(input(itVal)<value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];   		   						}
					else if (predicate=="lesserThanEquals"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
			                		outputPos+=(input(itVal)<=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];	
   					}
					else if (predicate=="notEquals"){
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
			                		outputPos+=(input(itVal)!=value.asInstanceOf[Rep[Float]]).asInstanceOf[Rep[Int]];
      					}
	   				else {
							input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
							outputPos+=1;
					}
				}
				else if (predicate=="equals"){
					if (input(itVal)==value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;
					}
				}
   				else if (predicate=="greaterThan"){
					if (input(itVal)>value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;
					}
   				}	
				else if (predicate=="greaterThanEquals"){
					if (input(itVal)>=value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;
					}
   				}
				else if (predicate=="lesserThan"){
					if (input(itVal)<value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;					
					}
   				}		
				else if (predicate=="lesserThanEquals"){
					if (input(itVal)<=value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;					
					}
  				}
				else if (predicate=="notEquals"){
					if (input(itVal)!=value.asInstanceOf[Rep[Float]]){
						input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
						outputPos+=1;
					}
				}
   				else {
					input(varIntToRepInt(outputPos)+maxNumIt+(2*threadsDisplacement)+3)=input(itVal)
					outputPos+=1;
				}
			}//End of def runInstructionUnrollResidue

		} //End of Abstract Loop Representation	

		/*Dummy class that enforces the use of parametric constuctor.*/
		class LoopRepresentation (maxNumIt: Rep[Int], valueForComparison: Rep[Float], predicate:String) extends AbstractLoopRepresentation(maxNumIt,valueForComparison,predicate){
		}


 		/*Initialization of the iteration space*/       
		val iterationSpace = new LoopRepresentation(maxNumIt, valueForComparison, predicate)

		/*Application of variants pased as an array of strings, and values of configuration variables*/
		for (instruction<-instructions){
			iterationSpace.applyVariant(instruction)

			/*If we want different configurations for succesive unrolling, vectorization or parallelization, the 
			local variables determining unroll depth, number of threads and size of vectorized instructions
			should be changed here.*/
		}

		iterationSpace.runLoop();
		
		/*Printing of the output array*/	
		println("Output array: ");
		for (i <- (0 until varIntToRepInt(outputPos)): Rep[Range]) {
        	  println(input(i+3+(2*iterationSpace.getThreadsDisplacement())+maxNumIt));
        	}
		if(varIntToRepInt(outputPos)==varIntToRepInt(zero)){
		  println("No results found.")
		}

      	}//End of snippet function

    /*End of area where context is shared between 2 stages*/

    }//End of use of DSL driver

    check("ScanVariants_"+predicate, snippet.code, "c")     /*The naming scheme can be modified in this line*/
   
   var fileLines = io.Source.fromFile("src/out/c_code_generation_tests_ScanVariants_"+predicate+".check.c").getLines.toList
   if(fileLines.filter(x=>x.contains("x0[2]")).length>0){
	   val numThreadsLine=fileLines.filter(x=>x.contains("x0[2]")).head;
   	   var arrayOfSplitting: Array[java.lang.String] = numThreadsLine.split(" ")
   val numThreadsIdentifier=arrayOfSplitting(3)
   val loopHeaders= fileLines.filter(x=> x.contains("for(")).filter(x=> x.contains("< "+numThreadsIdentifier))
   if (loopHeaders.length>0){
   	val prefixSumHeader=loopHeaders(0);
   	val parallelChunkHeader=loopHeaders(2);
   	arrayOfSplitting= prefixSumHeader.split("=0")
   	arrayOfSplitting=arrayOfSplitting(0).replace("  for(","").split(" ");
   	val integerRepresentation= arrayOfSplitting(0);
   	val iteratorName_prefixSum=arrayOfSplitting(1);
   	arrayOfSplitting = parallelChunkHeader.split("=0")
   	arrayOfSplitting=arrayOfSplitting(0).replace("  for(","").split(" ");
   	val iteratorName_parallelChunk=arrayOfSplitting(1);
   	def insert[A](xs: List[A], extra: List[A])(p: A => Boolean) = {
  		xs.map(x => if (p(x)) extra ::: List(x) else List(x)).flatten
   	}
	var auxList  = List[String]()
	auxList= "  pthread_t threads[("+integerRepresentation+")"+numThreadsIdentifier+"];"::auxList
	auxList=auxList:+("  "+integerRepresentation+" *inputArray;")
        auxList=auxList:+("  inputArray=("+integerRepresentation+"*)malloc("+numThreadsIdentifier+"*sizeof("+integerRepresentation+"));")
	fileLines=insert(fileLines,auxList) {_ == prefixSumHeader}
	var str: String =" ";
	var forHeaderDetected: Boolean = false;
	var firstMessageSpotted: Boolean = false;
	var doNothing: Boolean = false;
	auxList  = List[String]()
	var outputList  = List[String]()
	for (str<- fileLines){
		if (!forHeaderDetected){
			if (str==prefixSumHeader){
				forHeaderDetected=true;
			}
			else{
				outputList=outputList:+(str)
			}
		}
		else{
		    if (!firstMessageSpotted){
			auxList=auxList:+(str)
			if(str.contains("//#parallel prefix sum")){
				firstMessageSpotted=true;
			}
		    }
		    else if (!doNothing){
			auxList=auxList:+(str)
			if(str.contains("//#parallel prefix sum")){
				doNothing=true;
			}
		    }
		}
	}
	outputList=outputList:+("  void* parallelPrefixSum(void* input){")
	outputList=outputList:+("    "+integerRepresentation+" "+iteratorName_prefixSum+"=*("+integerRepresentation+"*)input;")
        outputList=outputList++auxList
	outputList=outputList:+("  }")
	outputList=outputList:+(prefixSumHeader)
	outputList=outputList:+("	inputArray["+iteratorName_prefixSum+"]="+iteratorName_prefixSum+";")
	outputList=outputList:+("	pthread_create(&threads["+iteratorName_prefixSum+"], NULL, parallelPrefixSum, (void *)&inputArray["+iteratorName_prefixSum+"]);")
	outputList=outputList:+("  }")
	outputList=outputList:+(prefixSumHeader)
	outputList=outputList:+("	pthread_join(threads["+iteratorName_prefixSum+"], NULL);")
	outputList=outputList:+("  }")
	
	auxList  = List[String]()
	forHeaderDetected= false;
	firstMessageSpotted= false;
	var secondMessageSpotted:Boolean= false;
	var thirdMessageSpotted:Boolean= false;
	var forthMessageSpotted:Boolean= false;
	var trailingLineRemoved:Boolean= false;
	doNothing= false;

	for (str<- fileLines){
		if (!firstMessageSpotted){
			if(str.contains("//#parallel prefix sum")){
				firstMessageSpotted=true;
			}
		}
		else if (!secondMessageSpotted){
			if(str.contains("//#parallel prefix sum")){
				secondMessageSpotted=true;
			}
		}
		else if(!trailingLineRemoved){
			trailingLineRemoved=true;
		}
		else if (!forHeaderDetected){
			if(str!=parallelChunkHeader){
				outputList=outputList:+(str)
			}
			else{
				forHeaderDetected=true;
			}
		}
		else if (!thirdMessageSpotted){
			auxList=auxList:+(str)
			if(str.contains("//#parallel chunk")){
				thirdMessageSpotted=true;
			}			
		}	
		else if (!forthMessageSpotted){
			auxList=auxList:+(str)
			if(str.contains("//#parallel chunk")){
				forthMessageSpotted=true;
			}			
		}			
	}
	outputList=outputList:+("  void* parallelChunk(void* input){");
	outputList=outputList:+("    "+integerRepresentation+" "+iteratorName_parallelChunk+"=*("+integerRepresentation+"*)input;")
        outputList=outputList++auxList
	outputList=outputList:+("  }")
	outputList=outputList:+(parallelChunkHeader)
	outputList=outputList:+("  	pthread_create(&threads["+iteratorName_parallelChunk+"], NULL, parallelChunk, (void *)&inputArray["+iteratorName_parallelChunk+"]);") 
	outputList=outputList:+("  }")
	outputList=outputList:+(parallelChunkHeader)
	outputList=outputList:+("	pthread_join(threads["+iteratorName_parallelChunk+"], NULL);")
	outputList=outputList:+("  }")
	firstMessageSpotted=false;
	secondMessageSpotted=false;
	trailingLineRemoved= false;
	for (str <-fileLines){
		if(!firstMessageSpotted){
			if(str.contains("//#parallel chunk")){
				firstMessageSpotted=true;
			}
		}
		else if (!secondMessageSpotted){
			if(str.contains("//#parallel chunk")){
				secondMessageSpotted=true;
			}
		}
		else if (!trailingLineRemoved){
				trailingLineRemoved=true;
		}
		else{
			outputList=outputList:+(str)
		}
	}	
	val pw = new PrintWriter(new File("src/out/c_code_generation_tests_ScanVariants_"+predicate+".check.c"))
	for (str<- outputList){
		pw.write(str+"\n");
	}
	pw.close();
   }}
  } //End of test "Scan Variants"
} //End of class ScanVariantsTest

