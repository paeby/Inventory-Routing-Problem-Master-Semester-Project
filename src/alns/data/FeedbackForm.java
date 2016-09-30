/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alns.data;

/**
 * The feedback form holds the data about the schedule generation for statistic 
 * collection. The data collected is: 
 * - The time it took to run the operations (destroy and repair)
 * - The number of times the operators (destroy and repair) were applied (rho)
 * 
 * @author raphael
 * @version 2.0
 */
public class FeedbackForm {
    
    // Operator duration data
    private long destroyOperatorDuration;
    private long repairOperatorDuration;
    
    // Number of times opperators were applied (rho)
    private int nbTimesAppliedDestroyOp;
    private int nbTimesAppliedRepairOp;
    
    /**
     * Constructor of FeedbackForm
     * 
     */
    public FeedbackForm() {
        // Initialize data
        this.destroyOperatorDuration = -1;
        this.repairOperatorDuration = -1;
        
        this.nbTimesAppliedDestroyOp = 0;
        this.nbTimesAppliedRepairOp = 0;
    }
    
    /**
     * Get the time it took to apply the destroy operator.
     * 
     * @return destroyOperatorDuration
     */
    public long getDestroyOperatorDuration() {
        return this.destroyOperatorDuration;
    }
    
    /**
     * Get the time it took to apply the repair operator.
     * 
     * @return repairOperatorDuration
     */
    public long getRepairOperatorDuration() {
        return this.repairOperatorDuration;
    }
    
    /**
     * Get the number of times the destroy operator was applied (rho).
     * 
     * @return nbTimesAppliedDestroyOp
     */
    public int getNbTimesAppliedDestroyOp() {
        return this.nbTimesAppliedDestroyOp;
    }
    
    /**
     * Get the number of times the repair operator was applied (rho).
     * 
     * @return nbTimesAppliedRepairOp
     */
    public int getNbTimesAppliedRepairOp() {
        return this.nbTimesAppliedRepairOp;
    }
    
    /**
     * Enter the duration of the operators to the form.
     * 
     * @param durationData
     */
    public void setDurationData(long[] durationData) {
        this.destroyOperatorDuration = durationData[0];
        this.repairOperatorDuration = durationData[1];

    }
    
    /**
     * Enter the number of times the operators were used to the form.
     * 
     * @param nbTimesAppliedData
     */
    public void setNbTimesAppliedData(int[] nbTimesAppliedData) {
        this.nbTimesAppliedDestroyOp = nbTimesAppliedData [0];
        this.nbTimesAppliedRepairOp = nbTimesAppliedData[1];
    }

}
