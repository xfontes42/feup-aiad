package agents;

import behaviours.broker.BrokerBusinessStarter;
import sajas.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import launchers.EnergyMarketLauncher;
import utils.AgentType;
import utils.GraphicSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Broker extends DFRegisterAgent {

    private HashMap<AID, Producer> producersInContractWith = new HashMap<>();

    private static final int TIMEOUT = 2000;

    private DFSearchAgent search;
    private boolean canStillBuyEnergy;
    private int duration = 365; // TODO: remove this at a further development stage

    public Broker(EnergyMarketLauncher model, GraphicSettings graphicSettings, int initialBudget) {
        super(model, graphicSettings);
        this.search = new DFSearchAgent(model, graphicSettings);

        this.setType(AgentType.BROKER);
        this.search.setType(AgentType.PRODUCER);

        this.moneyWallet.inject(initialBudget);
        this.canStillBuyEnergy = true;
    }

    @Override
    protected void setup() {
        super.setup();
        this.register();
        this.addBehaviour(new BrokerBusinessStarter(this, TIMEOUT));
    }

    /**
     * Fetches the list of promising (available) producers for new contracts.
     * @return the list of producers.
     */
    public List<String> getPromisingProducers() {
        ArrayList<String> producersNames = new ArrayList<>();

        for (DFAgentDescription p : this.search.searchAndGet()) {
            producersNames.add(p.getName().getLocalName());
        }
        return producersNames;
    }

    public EnergyMarketLauncher getWorldModel() {
        return worldModel;
    }

    public boolean canStillBuyEnergy(){
        return canStillBuyEnergy;
    }

    public void setCanStillBuyEnergy(boolean b){
        this.canStillBuyEnergy = b;
    }

    public int getDuration(){
        return duration;
    }

}
