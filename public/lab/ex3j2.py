import pandas as pd
data=pd.Series([0.25,0.5,0.75,1.0,1.25], index=['a','b','c','d','e'])
A=data['a':'c']
print(A)
B=data[(data>0.3)&(data<0.8)]
print(B)
C=print(data[['a','e']])
print(C)
