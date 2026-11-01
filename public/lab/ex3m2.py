import pandas as pd
import numpy as np
rng = np.random.RandomState(42)
A=pd.DataFrame(rng.randint(0,20,(2,2)),columns=list('AB'))
print(A)
B=pd.DataFrame(rng.randint(0,10,(3,3)),columns=list('BAC'))
print(B)
print(A+B)
fill=A.stack().mean()
print(fill)
print(A.add(B,fill_value=fill))
