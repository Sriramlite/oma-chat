import pandas as pd
area=pd.Series({'Alaska':1723337,'Texas':695662,'California':423967,},name='area')
population=pd.Series({'California':38332521,'Texas':26448193,'New York':19651127},name='population')
data=population/area
print(data)
data1=area.index.union(population.index)
print(data1)

A=pd.Series([2,4,6],index=[0,1,2])
B=pd.Series([1,3,5],index=[1,2,3])
print(A+B)
print(A.add(B,fill_value=0))
