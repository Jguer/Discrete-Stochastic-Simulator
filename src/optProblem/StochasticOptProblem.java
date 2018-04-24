package optProblem;

import java.io.File;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.LinkedList;
import java.util.List;
import parser.OptProblemHandler;
import pec.Pec;

/**
 * @author antonio
 * Class of the Optimization Problem. We have 4 kinds of attributes that are non static:
 * time related, individual related, event related and map related.
 * There is a static field containing the EventCompartor. This can be the same for all instances of Optimization Problems.
 */
public class StochasticOptProblem implements OptProblem{
	
	//ATTRIBUTES
	
	//time related fields
	double actual_time;
	double max_time;
	
	//individual related fields
	int alive_inds;
	int total_inds;
	int max_inds;
	List<Individual> list_inds;
	Individual best;
	int k;
	
	//event related fields
	int num_events;
	int num_deaths;
	int num_moves;
	int num_reprs;
	int num_epidemics;
	int num_ControlPrint = 1;
	int death_mean;
	int move_mean;
	int repr_mean;
	Pec<Event> pec;
	
	//map related fields
	Map map;
	Point start;
	Point goal;
	boolean hit;
	
	
	static EventComparator ec = new EventComparator();
	
	//CONTRUCTORS
	/**
	 * Constructor with no arguments for the Optimization Problem. All it does is create the empty lists that will be used.
	 */
	public StochasticOptProblem() {
		pec = new Pec<Event>();
		list_inds = new LinkedList<Individual>();
	}
	
	/**
	 * More advanced constructor that actually has values to put in the variables.
	 * It invokes the no arg constructor so we dont have to write the same thing twice.
	 * @param max_indss is the maximum number of alive individuals.
	 * @param max_timee is the total simulation time.
	 * @param dmean is the value to use in the mean of the death events.
	 * @param mmean is the value to use in the mean of the move events.
	 * @param rmean is the value to use in the mean of the reproduction events.
	 * @param xx is the number of columns of the map (possible X values will go from 1 to xx inclusive).
	 * @param yy is the number of rows of the map (possible Y values will go from 1 to yy inclusive).
	 * @param no is the number of obstacles in the map.
	 * @param cmaxx is the maximum cost of a Special Zone.
	 * @param kk is the comfort sensitivity parameter.
	 */
	public StochasticOptProblem(int max_indss, double max_timee, int dmean, int mmean, int rmean, int xx, int yy, int no, int cmaxx, int kk){
		this();
		actual_time = 0;
		max_time = max_timee;
		max_inds = max_indss;
		death_mean = dmean;
		move_mean = mmean;
		repr_mean = rmean;
		map = new Map(xx,yy,no,cmaxx);
		k = kk;
		hit = false;
	}
	
	//METHODS

	/**
	 * Method to create the first set of individuals. Each call of this method creates one new individual at the start point.
	 * The Death event is added to the PEC and the Move and Reproduction are only added if they occur prior to the Death.
	 * Should only be called when the Optimization Problem object has been correctly initialized using the full constructor.
	 */
	public void createFirstInds(){
		
		//create individual
		
		Individual ind = new Individual(total_inds++);
		
		alive_inds ++;
		list_inds.add(ind);
	
		ind.history.add(start);		
		ind.cost = 0;
		ind.costs.add(1);
		
		ind.updateComfort(goal,map.cmax, map.mapDimensions.x, map.mapDimensions.y, k);
		ind.death_time = actual_time + Event.expRandom(ind.getValueForExpMean()*death_mean);
		pec.addElement(new EvDeath(ind.death_time, ind), ec);
		
		// create move and only add if happens before death
		double randTime = actual_time + Event.expRandom(ind.getValueForExpMean()*move_mean);
		if(randTime<ind.death_time) {
			pec.addElement(new EvMove(randTime, ind), ec);
		}
		
		//create reproduction and only add if happens before death
		randTime = actual_time + Event.expRandom(ind.getValueForExpMean()*repr_mean);
		if(randTime<ind.death_time) {
			pec.addElement(new EvRepr(randTime, ind), ec);
		}
		
	}	

	
	public void initialize(int max_indss, double max_timee, int dmean, int mmean, int rmean, Map parsedMap, Point initialPoint, Point finalPoint, int kk, int num_inds_init){
		
		
		actual_time = 0;
		max_time = max_timee;
		max_inds = max_indss;
		death_mean = dmean;
		move_mean = mmean;
		repr_mean = rmean;
		map = parsedMap;
		k = kk;
		hit = false;
		
		int num_ctrl = 20;
		double ctrl_time = (double)max_time/num_ctrl;
		
		start = initialPoint;
		goal = finalPoint;
		

		for(int i=0; i<num_inds_init; i++) { //creates the first individuals at the starting point
			this.createFirstInds(); 
		}
		this.best = Individual.updateBest(list_inds, best, hit); //updates the best one so far
		
		for(int j=1;j<=num_ctrl;j++) { //adding the Control Print events to the PEC
			pec.addElement(new EvControlPrint(ctrl_time*j), ec);
		}
		
		//System.out.println(op.pec.toString());

	}
	
	public void simulate(){
		
		Event ev;
						
		// ================= SIMULATING =============================
		while(this.alive_inds > 0 && this.actual_time < this.max_time) {

			
			ev = this.pec.getFirstElement(); //get next event from PEC
			//System.out.println("ev: " +ev);
			this.actual_time = ev.time; //fast forward until its time to execute it
			this.num_events++;
			ev.ExecEvent(this);
			
			if(ev.individual!= null) { //if it was an event with an individual associated
				ev.individual.updateComfort(this.goal, this.map.cmax,  this.map.mapDimensions.x,  this.map.mapDimensions.y, this.k);
				this.best = Individual.updateBest(list_inds, best, hit); //updates the best one so far
			
				if(this.alive_inds>this.max_inds) {//launch an epidemic - it will have time = current time so we will execute it right away
					this.pec.addElement(new EvEpidemic(this), ec);
				}
			}
		}
		
		// ======================= WE'RE OUT OF THE SIMULATION!!! ===============================
		System.out.println("\n\n");
		if(this.actual_time>=this.max_time)
			System.out.println("Simulation ended because of the TIME LIMIT.");
		else if(this.alive_inds <= 0)
			System.out.println("Simulation ended because WE HAD NO MORE INDIVIDUALS");
		
		System.out.println("Total nº Events: " + this.num_events);
		System.out.println("Total nº Repr: " + this.num_reprs);
		System.out.println("Total nº Move: " + this.num_moves);
		System.out.println("Total nº Death: " + this.num_deaths);
		System.out.println("Total nº Epidemics: " + this.num_epidemics);
		
		System.out.println("Hit the goal? " + this.hit);
		System.out.println("Path of the best fit individual: " +  this.best.history.toString());
		System.out.println("Cost: " + this.best.cost);
		System.out.println("Comfort: " + this.best.comfort);
		
		
	}
	
	public void runOptimizationProblem(String filename) {
		
		//max_indss, max_timee,  dmean,  mmean,  rmean, x,  y, no, cmaxx, k, initial nº individuals){
		
		try {
	    	 SAXParserFactory factory = SAXParserFactory.newInstance();
	         SAXParser saxParser = factory.newSAXParser();
	         OptProblemHandler myhandler = new OptProblemHandler();
	         File inputFile = new File(filename);
	         saxParser.parse(inputFile, myhandler);     
	         
	         System.out.println(myhandler.getMaxpop() +" "+myhandler.getFinalinst()+" "+myhandler.getInitpop()+" "+myhandler.getComfortsens());
	         System.out.println(myhandler.getMap());
	         System.out.println(myhandler.getDmean() + " " + myhandler.getRmean() + " " + myhandler.getMmean());
	    
	 		 this.initialize(myhandler.getMaxpop(), myhandler.getFinalinst(), myhandler.getDmean(), myhandler.getMmean(),  myhandler.getRmean(),
	 				 myhandler.getMap(),myhandler.getInitialPoint(),myhandler.getFinalPoint(), myhandler.getComfortsens(), myhandler.getInitpop());
	 		
	 		 this.simulate();
	         
	         
	      } catch (Exception e) {
	         e.printStackTrace();
	      }       
		
		
	}
	
}
	
	/**
	 * Main method to run an optimization problem.
	 * The flow is the following:
	 * 1) Create the starting set of individuals and add their events to the PEC
	 * 2) In a cycle, remove the first event in the PEC and execute it
	 * 3) During 2) always check if there is a need for an epidemic.
	 * 4) The cycle ends when there are no more alive individuals or the simulation time is over.
	 * @param args are the arguments passed to the main function when executing the program.
	 */