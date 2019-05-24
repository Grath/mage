package mage.cards.v;

import mage.MageInt;
import mage.abilities.Ability;
import mage.abilities.common.DiesTriggeredAbility;
import mage.abilities.effects.common.DestroyTargetEffect;
import mage.abilities.effects.common.GainLifeEffect;
import mage.abilities.keyword.LifelinkAbility;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.SubType;
import mage.filter.StaticFilters;
import mage.filter.common.FilterControlledLandPermanent;
import mage.filter.common.FilterControlledPermanent;
import mage.target.common.TargetCreaturePermanent;

import java.util.UUID;

/**
 * @author JayDi85
 */
public final class VengeantVampire extends CardImpl {

    public VengeantVampire(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId, setInfo, new CardType[]{CardType.CREATURE}, "{5}{G}{G}");
        this.subtype.add(SubType.DINOSAUR);
        this.power = new MageInt(7);
        this.toughness = new MageInt(7);

        // Lifelink
        this.addAbility(LifelinkAbility.getInstance());

        // When Vengeant Vampire dies, destroy target creature an opponent controls and you gain 4 life.
        Ability ability = new DiesTriggeredAbility(new DestroyTargetEffect());
        ability.addEffect(new GainLifeEffect(4).concatBy("and"));
        ability.addTarget(new TargetCreaturePermanent(StaticFilters.FILTER_OPPONENTS_PERMANENT_CREATURE));
        this.addAbility(ability);
    }

    public VengeantVampire(final VengeantVampire card) {
        super(card);
    }

    @Override
    public VengeantVampire copy() {
        return new VengeantVampire(this);
    }
}
