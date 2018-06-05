/*Using the object-oriented capabilities available in the Scala language the next object (models) performs all calculations that respond
the question: is there a reliable method to predict the probability a customer experience fraud?.
The model object contains five (5) methods each one, as their name indicates, estimate a different classification algorithm.
After conscisness checking of this source code is possible to verify a frequent pattern on it's structure:
	1. There's always an assembler who "compress" the features into a single shape, the scala vector shape.
	2. The algorithm who gives the name to the object's method. Each one is a different scala class imported from the MLLib clases.
	3. An array which takes the percentage parameter to create two data sets. First for estimation, second for prediction.
	4. The pipeline instance who takes two stages: assembler and the method's algorithm.
	5. A multidimensional grid for cross-validation procedure that is performed on the estimation set. 
	6. The metric used in the cross-validation is set as the evaluator value.
	7. cv (cross-validator) gathers the pipeline, the evaluator, the paramGrid values and the number of folds.
	8. cvModel store the result (the "best" model) using the estimation data. This instance is saved by the cv.save.
	9. The prediction data set is transformed using the cvModel. The "best" model provides a probability score and class
	   to each row, i.e., to each customer whose data weren't used to fit the algorithm.
   	10. The final results are displayed in a dataframe structure.*/
object models {
def decision_tree(features:Array[String], percentage:Double, depth:Array[Int], folds:Int) : org.apache.spark.sql.DataFrame = {
val assembler = new VectorAssembler().setInputCols(features).setOutputCol("features")
val dt = new DecisionTreeClassifier().setLabelCol("fraude").setFeaturesCol("features")
val Array(trainingData,testData) = base_modelo.randomSplit(Array(percentage, 1 - percentage))
val pipeline = new Pipeline().setStages(Array(assembler, dt))
val paramGrid = new ParamGridBuilder().addGrid(dt.maxDepth, depth).addGrid(dt.impurity, Array("gini", "entropy")).build()
val evaluator = new BinaryClassificationEvaluator
evaluator.setMetricName("areaUnderPR").setLabelCol("fraude")
val cv = new CrossValidator().setEstimator(pipeline).setEvaluator(evaluator).setEstimatorParamMaps(paramGrid).setNumFolds(folds)
trainingData.cache()
testData.cache()
val cvModel = cv.fit(trainingData)
cvModel.save("dt_fraud_model")
val predictions = cvModel.transform(testData)
predictions.cache()
val results = predictions.withColumn("label", when($"fraude"===1.0, "FRAUDE").otherwise("NO_FRAUDE")).withColumn("predicted_label", when($"prediction" === 1.0,"FRAUDE").otherwise("NO_FRAUDE")).groupBy("label", "predicted_label").agg(count("documento").alias("clientes")).orderBy("label", "predicted_label")
return results
}
def ada_boost(features:Array[String], percentage:Double, depth:Array[Int], n_trees:Array[Int], folds:Int):org.apache.spark.sql.DataFrame={
val assembler = new VectorAssembler().setInputCols(features).setOutputCol("features")
val gbt = new GBTClassifier().setLabelCol("fraude").setFeaturesCol("features")
val Array(trainingData, testData) = base_modelo.randomSplit(Array(percentage, 1 - percentage))
val pipeline = new Pipeline().setStages(Array(assembler, gbt))
val paramGrid = new ParamGridBuilder().addGrid(gbt.maxDepth, depth).addGrid(gbt.maxIter, n_trees).addGrid(gbt.impurity, Array("gini", "entropy")).build()
val evaluator = new BinaryClassificationEvaluator
evaluator.setMetricName("areaUnderPR").setLabelCol("fraude")
val cv = new CrossValidator().setEstimator(pipeline).setEvaluator(evaluator).setEstimatorParamMaps(paramGrid).setNumFolds(folds)
trainingData.cache()
testData.cache()
val cvModel = cv.fit(trainingData)
cvModel.save("gbt_fraud_model")
val predictions = cvModel.transform(testData)
predictions.cache()
val results = predictions.withColumn("label", when($"fraude"===1.0,"FRAUDE").otherwise("NO_FRAUDE")).withColumn("predicted_label", when($"prediction" === 1.0, "FRAUDE").otherwise("NO_FRAUDE")).groupBy("label", "predicted_label").agg(count("documento").alias("clientes")).orderBy("label", "predicted_label")
return results
}
def random_forest(features:Array[String], percentage:Double, depth:Array[Int], n_trees:Array[Int], folds:Int) : org.apache.spark.sql.DataFrame = {
val assembler = new VectorAssembler().setInputCols(features).setOutputCol("features")
val rf = new RandomForestClassifier().setLabelCol("fraude").setFeaturesCol("features")
val Array(trainingData, testData) = base_modelo.randomSplit(Array(percentage, 1 - percentage))
val pipeline = new Pipeline().setStages(Array(assembler, rf))
val paramGrid = new ParamGridBuilder().addGrid(rf.maxDepth, depth).addGrid(rf.numTrees, n_trees).addGrid(rf.impurity, Array("gini", "entropy")).build()
val evaluator = new BinaryClassificationEvaluator
evaluator.setMetricName("areaUnderPR").setLabelCol("fraude")
val cv = new CrossValidator().setEstimator(pipeline).setEvaluator(evaluator).setEstimatorParamMaps(paramGrid).setNumFolds(folds)
trainingData.cache()
testData.cache()
val cvModel = cv.fit(trainingData)
cvModel.save("rf_fraud_model")
val predictions = cvModel.transform(testData)
predictions.cache()
val results = predictions.withColumn("label", when($"fraude" === 1.0, "FRAUDE").otherwise("NO_FRAUDE")).withColumn("predicted_label",when($"prediction"===1.0,"FRAUDE").otherwise("NO_FRAUDE")).groupBy("label", "predicted_label").agg(count("documento").alias("clientes")).orderBy("label", "predicted_label")
return results
}
def logistic_regression(features:Array[String], percentage:Double) : org.apache.spark.sql.DataFrame = {
val assembler = new VectorAssembler().setInputCols(features).setOutputCol("features")
val lr = new LogisticRegression().setFamily("binomial").setLabelCol("fraude").setFeaturesCol("features")
val Array(trainingData, testData) = base_modelo.randomSplit(Array(percentage, 1 - percentage))
val pipeline = new Pipeline().setStages(Array(assembler, lr))
trainingData.cache()
testData.cache()
val model = pipeline.fit(trainingData)
model.save("lr_fraud_model")
val predictions = model.transform(testData)
predictions.cache()
val results = predictions.withColumn("label", when($"fraude" === 1.0, "FRAUDE").otherwise("NO_FRAUDE")).withColumn("predicted_label",when($"prediction"===1.0,"FRAUDE").otherwise("NO_FRAUDE")).groupBy("label", "predicted_label").agg(count("documento").alias("clientes")).orderBy("label", "predicted_label")
return results
}
def naive_Bayes(features:Array[String], percentage:Double, apriori:String) : org.apache.spark.sql.DataFrame = {
val assembler = new VectorAssembler().setInputCols(features).setOutputCol("features")
val nb = new NaiveBayes().setLabelCol("fraude").setFeaturesCol("features").setModelType(apriori)
val Array(trainingData, testData) = base_modelo.randomSplit(Array(percentage, 1 - percentage))
val pipeline = new Pipeline().setStages(Array(assembler, nb))
trainingData.cache()
testData.cache()
val model = pipeline.fit(trainingData)
model.save("nb_fraud_model")
val predictions = model.transform(testData)
predictions.cache()
val results = predictions.withColumn("label", when($"fraude" === 1.0, "FRAUDE").otherwise("NO_FRAUDE")).withColumn("predicted_label",when($"prediction"===1.0,"FRAUDE").otherwise("NO_FRAUDE")).groupBy("label", "predicted_label").agg(count("documento").alias("clientes")).orderBy("label", "predicted_label")
return results
}
}
