package outlierdetection;

import java.util.Set;

import mtree.ComposedSplitFunction;
import mtree.DistanceFunction;
import mtree.DistanceFunctions;
import mtree.MTree;
import mtree.PartitionFunctions;
import mtree.PromotionFunction;
import mtree.tests.Data;
import mtree.utils.Pair;
import mtree.utils.Utils;

public class MTreeClass extends MTree<Data> {

    private static final PromotionFunction<Data> nonRandomPromotion = new PromotionFunction<Data>() {
        @Override
        public Pair<Data> process(Set<Data> dataSet, DistanceFunction<? super Data> distanceFunction) {
            return Utils.minMax(dataSet);
        }
    };

    public MTreeClass() {
        super(25, DistanceFunctions.EUCLIDEAN, new ComposedSplitFunction<Data>(nonRandomPromotion,
                new PartitionFunctions.BalancedPartition<Data>()));
    }

    public void add(Data data) {
        super.add(data);
        _check();
    }

    public boolean remove(Data data) {
        boolean result = super.remove(data);
        _check();
        return result;
    }

    DistanceFunction<? super Data> getDistanceFunction() {
        return distanceFunction;
    }
};

