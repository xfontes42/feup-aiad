package agents;

import behaviours.broker.BrokerBusinessStarter;
import behaviours.broker.BrokerListeningBehaviour;
import sajas.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import launchers.EnergyMarketLauncher;
import utils.AgentType;
import utils.GraphicSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The Broker class represents the middleman between the energy suppliers and the final consumers.
 */
public class Broker extends DFRegisterAgent {

    private HashMap<AID, Producer> producersInContractWith = new HashMap<>();

    private static final int TIMEOUT = 2000;

    private DFSearchAgent search;
    private boolean canStillBuyEnergy;
    private int duration = 365; // TODO: remove this at a further development stage

    public Broker(EnergyMarketLauncher model, GraphicSettings graphicSettings, int initialBudget) {
        super(model, graphicSettings, AgentType.BROKER);
        this.search = new DFSearchAgent(model, graphicSettings);

        this.search.setSearchType(AgentType.PRODUCER);

        this.moneyWallet.inject(initialBudget);
        this.canStillBuyEnergy = true;
    }

    @Override
    protected void setup() {
        super.setup();
        addBehaviour(new BrokerBusinessStarter(this, TIMEOUT));
        addBehaviour(new BrokerListeningBehaviour(this));
    }

    /**
     * Fetches the list of promising (available) producers for new contracts.
     *
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

    public boolean canStillBuyEnergy() {
        return canStillBuyEnergy;
    }

    public void setCanStillBuyEnergy(boolean b) {
        this.canStillBuyEnergy = b;
    }

    public int getDuration() {
        return duration;
    }

    public int getAvailableEnergy() {
        return (int) energyWallet.getBalance(); // TODO bem grande
    }

    public int getEnergyUnitSellPrice() {
        return (int) 100; // TODO bem grande
    }

}
