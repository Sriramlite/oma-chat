import matplotlib.pyplot as plt
import numpy as np
from sklearn import datasets, linear_model, metrics
import pandas as pd
df = pd.read_csv('pima_diabetes.csv')
data = df[['Age', 'Glucose', 'BMI', 'BloodPressure', 'Pregnancies']]
target = df[['Outcome']]
print(data)
print(target)

X = data
Y = target

from sklearn.model_selection import train_test_split
X_train, X_test, Y_train, Y_test = train_test_split(X, Y, test_size=0.3, random_state=101)

reg = linear_model.LinearRegression()

reg.fit(X_train, Y_train)
Y_predict = reg.predict(X_test)

print('Coefficients:', reg.coef_)

print('Variance score: {}'.format(reg.score(X_test, Y_test)))

from sklearn.metrics import r2_score
print('r^2:', r2_score(Y_test, Y_predict))

from sklearn.metrics import mean_squared_error
mse = mean_squared_error(Y_test, Y_predict)
rmse = np.sqrt(mse)
print('RMSE:', rmse)
                        
