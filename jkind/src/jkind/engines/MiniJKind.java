package jkind.engines; 
import jkind.JKindSettings; 
import jkind.api.ApiUtil;
import jkind.api.JKindApi;
import jkind.api.results.JKindResult;
import jkind.api.results.PropertyResult;
import jkind.api.results.Status;
import jkind.engines.ivcs.IvcUtil; 
import java.io.File; 
import java.util.List;
import java.util.Set;
 
import org.eclipse.core.runtime.NullProgressMonitor; 
import jkind.lustre.Node; 
import jkind.results.Counterexample; 
import jkind.results.InvalidProperty;
import jkind.results.Property;
import jkind.results.UnknownProperty;
import jkind.results.ValidProperty;
import jkind.translation.Specification;  

public class MiniJKind{  
	public static final String UNKNOWN = "UNKNOWN";
	public static final String UNKNOW_WITH_EXCEPTION = "UNKNOW_WITH_EXCEPTION";
	public static final String INVALID = "INVALID";
	public static final String VALID = "VALID";
	public static final String NOT_YET_CHECKED = "NOT_YET_CHECKED";
	private Node program; 
	private Counterexample invalidModel; 
	private double runtime;
	private String status = NOT_YET_CHECKED; 
	private JKindSettings mSettings;
	private int k;
	private List<String> invariants;
	private Set<String> ivc;
    
	public MiniJKind(Specification spec, JKindSettings settings) { 
		if (spec.node.properties.size() != 1) {
			throw new IllegalArgumentException("MiniJKind Expects exactly one property");
		}
		
		if (settings.allAssigned && settings.reduceIvc){ 
			program = IvcUtil.setIvcArgs(spec.node, IvcUtil.getAllAssigned(spec.node));
			mSettings = settings;
		}else{
			program = spec.node;
			mSettings = settings;
		}
		this.verify();
	}
	
	public void verify() {
		JKindResult result = initialJKindResult();
		JKindApi api = new JKindApi();
		setApiOptions(api);
		File lustreFile = null; 
		lustreFile = ApiUtil.writeLustreFile(program.toString());
		api.execute(lustreFile, result, new NullProgressMonitor());
		Property prop = result.getPropertyResults().get(0).getProperty();
		
		setRuntime(prop.getRuntime());
		
		if (prop instanceof ValidProperty) {
			setValidMessage((ValidProperty) prop);
		} else if (prop instanceof InvalidProperty) {
			setInvalid(((InvalidProperty) prop).getCounterexample());
		} else if (prop instanceof UnknownProperty) {
			setUnknown();
		} //else if (I need to make a case for status = UNKNOW_WITH_EXCEPTION; 
		  // or we could just get rid of this Exception in general)
	}
	 
	private void setApiOptions(JKindApi api) {
		if(mSettings.reduceIvc){
			api.setIvcReduction();
		}
		api.setN(mSettings.n);
		api.setSolver(mSettings.solver);
		api.setPdrMax(mSettings.pdrMax);
		api.setTimeout(mSettings.timeout); 
		api.setSlicing(mSettings.slicing);
	}

	private JKindResult initialJKindResult() {
		JKindResult result = new JKindResult("results");
		for (String prop : program.properties) {
			PropertyResult pr = result.addProperty(prop);
			pr.addPropertyChangeListener(evt -> {
				if (pr.getStatus() != Status.WORKING) {
					System.out.println("MiniJKind: " + pr);
				}
			});
		}
		return result;
	}
	
	public void setValidMessage(ValidProperty prop) {
		k = prop.getK();
		invariants = prop.getInvariants();
		ivc = prop.getIvc();
		status = VALID;
	}

	private void setInvalid(Counterexample cex) {
		status = INVALID;
		this.invalidModel = cex;
	}
	
	private void setRuntime(double rt){
		runtime = rt;
	}

	private void setUnknown() { 
		status = UNKNOWN;
	}
	
	public Counterexample getInvalidModel() {
		return invalidModel;
	}
	
	public double getRuntime(){
		return runtime;
	}
	public String getPropertyStatus() {
		return status;
	}
	
	public Set<String> getPropertyIvc() {
		return ivc;
	}
	
	public List<String> getPropertyInvariants() {
		return invariants;
	}
	
	public int getK() {
		return k;
	}
}
