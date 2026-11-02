import pandas as pd
data1=pd.Series([0.25,0.5,0.75,1.0],index=['a','b','c','d'])
print(data1)
print(data1['b'])
data2=pd.Series([0.25,0.5,0.75,1.0],index=[2,5,3,7])
print(data2)
print(data2[5])
