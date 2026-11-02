import pandas as pd
import numpy as np
rng=np.random.RandomState(42)
A=rng.randint(10,size=(3,4))
print(A)
print(A-A[0])
df=pd.DataFrame(A,columns=list('QRST'))
print(df.subtract(df['R'],axis=0))
halfrow=df.iloc[0,::2]
print(halfrow)
print(df-halfrow)
