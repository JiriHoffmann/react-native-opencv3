import { NativeModules } from 'react-native';

const { RNOpencv3 } = NativeModules;

const RNCv = RNOpencv3;
const resolveAssetSource = require('react-native/Libraries/Image/resolveAssetSource');
const downloadAssetSource = require('./downloadAssetSource');

const useCascadeOnImage = (cascade, image) => {
	return new Promise(async (resolve, reject) => {
		let finalUri = '';
		if (typeof image === 'string' && image.startsWith('file')) {
			finalUri = image.slice(6);
		} else {
			const sourceUri = await resolveAssetSource(image).uri;
			finalUri = await downloadAssetSource(sourceUri);
		}
		const srcMat = await RNCv.imageToMat(finalUri);
		RNOpencv3.useCascadeOnImage(cascade, srcMat)
			.then((res) => {
				if (res === null || res === '') {
					resolve([]);
				}
				let objects = JSON.parse(res).objects;
				if (objects) {
					resolve(objects);
				} else {
					reject(res);
				}
			})
			.catch((err) => {
				reject(err);
			});
	});
};

export { useCascadeOnImage };
