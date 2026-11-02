import pandas as pd
import numpy as np
df=pd.DataFrame(np.random.rand(3,2),
columns=['foo','bar'],index=['a','b','c'])
print(df)
