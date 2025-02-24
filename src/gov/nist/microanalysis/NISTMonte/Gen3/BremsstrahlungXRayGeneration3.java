package gov.nist.microanalysis.NISTMonte.Gen3;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import gov.nist.microanalysis.EPQLibrary.Bremsstrahlung;
import gov.nist.microanalysis.EPQLibrary.Element;
import gov.nist.microanalysis.EPQLibrary.Material;
import gov.nist.microanalysis.EPQLibrary.ToSI;
import gov.nist.microanalysis.NISTMonte.Electron;
import gov.nist.microanalysis.NISTMonte.MonteCarloSS;
import gov.nist.microanalysis.Utility.Math2;

/**
 * The BremsstrahlungXRayGeneration class implements the physics for
 * Bremsstrahlung emission.
 * 
 * @author nicholas
 */
final public class BremsstrahlungXRayGeneration3 extends BaseXRayGeneration3 {

   // The number of fractional Bremsstrahlung emissions to model each x-ray
   // event
   private final int mBremPerEvent = 10;

   private static double mMinEnergy = ToSI.eV(100.0);
   // Bookkeeping data
   transient private Map<Element, Bremsstrahlung> mBremInstances;
   transient private boolean mInitialized = false;
   transient private Random mRandom = new Random();

   /**
    * Use this static method instead of the constructor to create instances of
    * this class and initialize it with an instance of the MonteCarloSS class.
    * 
    * @param mcss
    * @return BremsstrahlungXRayGeneration
    */
   public static BremsstrahlungXRayGeneration3 create(MonteCarloSS mcss) {
      final BremsstrahlungXRayGeneration3 res = new BremsstrahlungXRayGeneration3();
      res.initialize(mcss);
      mcss.addActionListener(res);
      return res;
   }

   protected BremsstrahlungXRayGeneration3() {
      super("Bremsstrahlung", "Default");
      initialize();
   }

   private void initialize() {
      mBremInstances = new HashMap<Element, Bremsstrahlung>();
      mInitialized = true;
   }

   private Bremsstrahlung getBremsstrahlung(Element el) {
      Bremsstrahlung res = mBremInstances.get(el);
      if (res == null) {
         res = new Bremsstrahlung(el);
         mBremInstances.put(el, res);
      }
      return res;
   }

   /**
    * actionPerformed - Handles MonteCarloSS.XRayEvents by caching some useful
    * information then firing event listeners. The EventListeners should call
    * crossSection to access the Bremsstrahlung related information about the
    * current electron energy and position.
    * 
    * @param ae
    *           ActionEvent
    */
   @Override
   public void actionPerformed(ActionEvent ae) {
      if (!mInitialized)
         initialize();
      assert (ae.getSource() instanceof MonteCarloSS);
      final MonteCarloSS monte = (MonteCarloSS) ae.getSource();
      reset();
      switch (ae.getID()) {
         case MonteCarloSS.ScatterEvent :
         case MonteCarloSS.NonScatterEvent : {
            final Electron e = monte.getElectron();
            final double stepLen = e.stepLength();
            final Material mat = e.getCurrentRegion().getMaterial();
            if (mat.getElementCount() == 0)
               return;
            final double frac = mRandom.nextDouble();
            final double[] pos = Math2.pointBetween(e.getPrevPosition(), e.getPosition(), frac);
            final double ee = e.getPreviousEnergy() + frac*(e.getEnergy() - e.getPreviousEnergy());
            final double[] dir = e.getDirection();
            // Generate one fractional bremsstrahlung emission per ScatterEvent
            // Select the event by randomly selecting one element with weighting
            // determined by the relative total cross sections
            final Element[] elms = new Element[mat.getElementCount()];
            final double[] elProb = new double[mat.getElementCount()];
            double sumProb = 0.0;
            {
               int j = 0;
               for (final Element elm : mat.getElementSet()) {
                  elms[j] = elm;
                  final double p = mat.atomsPerCubicMeter(elm) * getBremsstrahlung(elm).sigma(ee) * stepLen;
                  // p is the (fractional) number of Bremsstrahlung photons
                  // generated by this element
                  if (p > 0) {
                     elProb[j] = p;
                     sumProb += p;
                  }
                  ++j;
               }
            }
            // sumProb is the total probability for any type of Bremsstrahlung
            // event at any possible energy due to an interaction
            // with any of the available elements. There are a couple of
            // different possible strategies to assigning the probable
            // Brem events. Logically, they are equivalent. I select to assign
            // the full probability to one element each iteration
            // through this method. This element is selected according to the
            // fraction of the total probability due to the
            // specified element.
            if (sumProb > 0.0) {
               // Select at random only one element for which to generate brem
               // but assign the full sumProp of Brem production.
               double r = mRandom.nextDouble() * sumProb;
               for (int j = 0; j < elProb.length; ++j) {
                  r -= elProb[j];
                  if (r <= 0) {
                     // Generate brem only for this element
                     final Bremsstrahlung b = getBremsstrahlung(elms[j]);
                     for (int i = 0; i < mBremPerEvent; ++i) {
                        final double energy = b.getRandomizedEvent(ee, mRandom.nextDouble());
                        if (energy > mMinEnergy)
                           addBremXRay(pos, energy, sumProb / mBremPerEvent, elms[j], dir, ee);
                     }
                     fireXRayListeners();
                     break; // only one element each time...
                  }
               }
            }
         }
            break;
         default :
            fireXRayListeners(ae.getID());
            break;
      }
   }

   /*
    * @see
    * gov.nist.microanalysis.EPQLibrary.AlgorithmUser#initializeDefaultStrategy
    * ()
    */
   @Override
   protected void initializeDefaultStrategy() {
   }

}
